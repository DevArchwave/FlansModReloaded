package com.flansmod.common.actions;

import com.flansmod.client.FlansModClient;
import com.flansmod.client.particle.GunshotHitBlockParticle;
import com.flansmod.common.FlansMod;
import com.flansmod.common.gunshots.*;
import com.flansmod.common.item.BulletItem;
import com.flansmod.common.projectiles.BulletEntity;
import com.flansmod.common.types.elements.ActionDefinition;
import com.flansmod.common.types.guns.*;
import com.flansmod.util.Maths;
import com.flansmod.util.Transform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ShootAction extends Action
{
	private final HashMap<Integer, GunshotCollection> Results;

	public static class ShootNetData extends Action.NetData
	{
		public static final int ID = 1;
		public static final ShootNetData Invalid = new ShootNetData();

		public final GunshotCollection Results;

		public ShootNetData()
		{
			Results = new GunshotCollection();
		}

		public ShootNetData(GunshotCollection results)
		{
			Results = results;
		}

		@Override
		public int GetID()
		{
			return ID;
		}

		@Override
		public void Encode(FriendlyByteBuf buf)
		{
			GunshotCollection.Encode(Results, buf);
		}

		@Override
		public void Decode(FriendlyByteBuf buf)
		{
			GunshotCollection.Decode(Results, buf);
		}
	}

	public ShootAction(@Nonnull ActionGroup group, @Nonnull ActionDefinition def)
	{
		super(group, def);
		Results = new HashMap<>();
	}

	@Nonnull
	@Override
	public Action.NetData GetNetDataForTrigger(int triggerIndex)
	{
		if (Results.containsKey(triggerIndex))
			return new ShootNetData(Results.get(triggerIndex));
		return ShootNetData.Invalid;
	}

	@Override
	public void UpdateFromNetData(Action.NetData netData, int triggerIndex)
	{
		if(netData instanceof ShootNetData shootNetData)
		{
			Results.put(triggerIndex, shootNetData.Results);
		}
	}

	@Override
	public boolean PropogateToServer(ActionGroupContext context) { return true; }
	@Override
	public boolean ShouldFallBackToReload(ActionGroupContext context)
	{
		if(!context.CanShoot(0))
		{
			if(context.CanBeReloaded(0))
				return true;
		}

		return false;
	}
	@Override
	public boolean CanStart(ActionGroupContext context)
	{
		if(!context.Gun().IsValid())
			return false;
		if(!context.Shooter().IsValid())
			return false;
		if(!context.GunDef().IsValid())
			return false;
		if(context.ActionStack().IsReloading())
			return false;
		if(context.ActionStack().GetShotCooldown() > 0.0f)
			return false;
		if(!context.CanShoot(0))
			return false;

		return super.CanStart(context);
	}

	@Override
	public boolean CanRetrigger(ActionGroupContext context)
	{
		if(!context.CanShoot(0))
			return false;
		if(!context.Gun().IsItemStackStillInPlace())
			return false;

		return true;
	}

	public boolean VerifyServer(ActionGroupContext context, GunshotCollection shots, int triggerIndex)
	{
		Results.put(triggerIndex, shots);

		// TODO: Big security pass needed

		// Verify that this shot makes sense by itself
		// TODO: Check if we can shoot based on our local data about our
		// a) Inventory, ammo levels
		// b) Shoot cooldown
		// c) Handedness

		// TODO: Random spot check later - run a little statistical analysis on this player's shots over some time period


		return true;
	}

	public void SetResults(GunshotCollection shots, int triggerIndex)
	{
		Results.put(triggerIndex, shots);
	}

	public boolean ValidateAndSetResults(GunContext context, GunshotCollection shots, int triggerIndex)
	{
		Results.put(triggerIndex, shots);

		// TODO: Verify that these shots could definitely come from this context

		return true;
	}

	public GunshotCollection GetResults(int triggerIndex)
	{
		return Results.get(triggerIndex);
	}

	private static final double RAYCAST_LENGTH = 500.0d;

	@Override
	public double GetPropogationRadius(ActionGroupContext context)
	{
		return context.Loudness();
	}
	@Override
	public void AddExtraPositionsForNetSync(ActionGroupContext context, int triggerIndex, List<Vec3> positions)
	{
		if(Results.containsKey(triggerIndex))
		{
			GunshotCollection shots = Results.get(triggerIndex);
			for(int i = 0; i < shots.Count(); i++)
			{
				positions.add(shots.Get(i).Endpoint());
			}
		}
	}
	public void Calculate(ActionGroupContext context, int repeatIndex)
	{
		GunshotCollection shots = null;
		if(Results.containsKey(repeatIndex))
			shots = Results.get(repeatIndex);
		else
		{
			Results.put(repeatIndex, shots = new GunshotCollection()
				.FromAction(context.InputType)
				.WithOwner(context.Owner())
				.WithShooter(context.Entity())
				.WithGun(context.GunDef()));
		}

		// If we are firing something faster than 1200rpm, that is more than 1 per tick
		// We are now handling repeat actions at the Action level, so ShootAction will just get multiple triggers
		int requestedShotsFired = 1; //context.ActionStack().TryShootMultiple(stats.TimeToNextShot());

		// We want to shoot {shotsFired} many, but check against and now consume ammo durability
		List<ItemStack> shotsFired = new ArrayList<>();
		for(int i = 0; i < requestedShotsFired; i++)
		{
			ItemStack bulletCheck = context.ConsumeOneBullet(0);
			if(!bulletCheck.isEmpty())
				shotsFired.add(bulletCheck);
		}

		for(int j = 0; j < shotsFired.size(); j++)
		{
			if(shotsFired.get(j).getItem() instanceof BulletItem bulletItem)
			{
				GunshotContext shotContext = GunshotContext.CreateFrom(context, bulletItem.Def());
				// Multiplier from https://github.com/FlansMods/FlansMod/blob/71ba7ed065d906d48f34ca471bbd0172b5192f6b/src/main/java/com/flansmod/common/guns/ShotHandler.java#L93
				float bulletSpread = 0.0025f * shotContext.Spread();
				for (int i = 0; i < shotContext.BulletCount(); i++)
				{
					Transform randomizedDirection = RandomizeVectorDirection(
						context.Shooter().Entity().level.random,
						context.Shooter().GetShootOrigin(),
						bulletSpread,
						shotContext.SpreadPattern());

					float penetrationPower = shotContext.PenetrationPower();

					if (shotContext.Bullet.shootStats.hitscan)
					{
						// Hitscan: Use the raytracer on client, find our hits and let the server know what they were
						// Server will verify these results
						List<HitResult> hits = new ArrayList<HitResult>(8);
						Raytracer.ForLevel(context.Shooter().Entity().level).CastBullet(
							context.Shooter().Entity(),
							randomizedDirection.PositionVec3(),
							randomizedDirection.ForwardVec3().scale(RAYCAST_LENGTH),
							penetrationPower,
							penetrationPower,
							hits
						);

						HitResult[] hitArray = new HitResult[hits.size()];
						hits.toArray(hitArray);
						shots.AddShot(new Gunshot()
							.FromShot(repeatIndex)
							.WithOrigin(randomizedDirection.PositionVec3())
							.WithTrajectory(randomizedDirection.ForwardVec3().scale(RAYCAST_LENGTH))
							.WithHits(hitArray)
							.WithBullet(bulletItem.Def()));
					}
					else
					{
						// Non-hitscan: The server will simulate the entity, so we just say where it should be going and leave them to it
						shots.AddShot(new Gunshot()
							.FromShot(repeatIndex)
							.WithOrigin(randomizedDirection.PositionVec3())
							.WithTrajectory(randomizedDirection.ForwardVec3().scale(shotContext.Speed()))
							.WithBullet(shotContext.Bullet));
					}
				}
			}
		}
	}

	private Transform RandomizeVectorDirection(RandomSource rand, Transform aim, float spread, ESpreadPattern spreadPattern)
	{
		Transform result = aim.copy();
		Vector3d yAxis = aim.Up();
		Vector3d xAxis = aim.Right();
		float xComponent;
		float yComponent;

		switch (spreadPattern)
		{
			case Circle, FilledCircle ->
			{
				float theta = rand.nextFloat() * Maths.TauF;
				float radius = (spreadPattern == ESpreadPattern.Circle ? 1.0f : rand.nextFloat()) * spread;
				xComponent = radius * Maths.SinF(theta);
				yComponent = radius * Maths.CosF(theta);
			}
			case Horizontal ->
			{
				xComponent = spread * (rand.nextFloat() * 2f - 1f);
				yComponent = 0.0f;
			}
			case Vertical ->
			{
				xComponent = 0.0f;
				yComponent = spread * (rand.nextFloat() * 2f - 1f);
			}
			case Triangle ->
			{
				// Random square, then fold the corners
				xComponent = rand.nextFloat() * 2f - 1f;
				yComponent = rand.nextFloat() * 2f - 1f;

				if (xComponent > 0f)
				{
					if (yComponent > 1.0f - xComponent * 2f)
					{
						yComponent = -yComponent;
						xComponent = 1f - xComponent;
					}
				} else
				{
					if (yComponent > xComponent * 2f + 1f)
					{
						yComponent = -yComponent;
						xComponent = -1f - xComponent;
					}
				}
			}
			default -> {
				xComponent = 0.0f;
				yComponent = 0.0f;
			}
		}

		float yaw = Maths.AtanF(xComponent);
		float pitch = Maths.AtanF(yComponent);

		result = result.RotateLocalYaw(yaw * Maths.RadToDegF);
		result = result.RotateLocalPitch(pitch * Maths.RadToDegF);

		return result;
	}

	@Override
	public void OnTriggerServer(ActionGroupContext context, int triggerIndex)
	{
		if(!context.Shooter().IsValid())
		{
			Group.SetFinished();
			return;
		}

		Level level = context.Shooter().Entity().level;
		// Process shots that were added for this re-trigger in particular
		GunshotCollection shotCollection = Results.get(triggerIndex);
		if(shotCollection != null)
		{
			for(Gunshot shot : shotCollection.Shots)
			{
				GunshotContext gunshotContext = GunshotContext.CreateFrom(context, shot.bulletDef);
				if(gunshotContext.IsValid())
				{
					if(gunshotContext.Bullet.shootStats.hitscan)
					{
						// Hitscan weapons we resolve the hits instantly
						ServerProcessImpact(level, shot, gunshotContext);
					}
					else
					{
						// Otherwise, a bullet entity will need to be spawned
						ServerSpawnBullet(level, shot, gunshotContext);
					}
				}
				else FlansMod.LOGGER.error("Invalid shot with bullet " + shot.bulletDef);
			}
		}
	}

	private void ServerSpawnBullet(Level level, Gunshot shot, GunshotContext gunshotContext)
	{
		BulletEntity bullet = new BulletEntity(FlansMod.ENT_TYPE_BULLET.get(), level);
		bullet.InitContext(gunshotContext);
		bullet.SetVelocity(shot.trajectory.scale(1d/20d));
		bullet.setPos(shot.origin);
		bullet.lookAt(EntityAnchorArgument.Anchor.FEET, shot.trajectory);
		level.addFreshEntity(bullet);
	}

	private void ServerProcessImpact(Level level, Gunshot shot, GunshotContext gunshotContext)
	{
		gunshotContext.ProcessImpact(level, shot);
	}

	@Override
	public void OnTriggerClient(ActionGroupContext context, int triggerIndex)
	{
		if(context.Shooter().IsLocalPlayerOwner())
		{
			Calculate(context, triggerIndex);
		}

		GunshotCollection shots = Results.get(triggerIndex);
		if(shots != null)
		{
			boolean hitEntity = false;
			boolean hitMLG = false;
			for(Gunshot shot : shots.Shots)
			{
				// Create client effects only for bullets that were added in this most recent re-trigger
				GunshotContext gunshotContext = GunshotContext.CreateFrom(context, shot.bulletDef);

				if(gunshotContext.Bullet.shootStats.hitscan)
				{
					// Create a bullet trail render
					FlansModClient.SHOT_RENDERER.AddTrail(shot.origin, shot.Endpoint());

					for (HitResult hit : shot.hits)
					{
						if (hit.getType() == HitResult.Type.ENTITY)
						{
							// Check bullet invulnerability
							if (!((EntityHitResult) hit).getEntity().isAttackable())
								continue;

							hitEntity = true;
							if (((EntityHitResult) hit).getEntity() instanceof EnderDragon dragon)
							{
								float damage = gunshotContext.ImpactDamage();
								damage = damage / 4.0F + Math.min(damage, 1.0F);
								if (dragon.getHealth() <= damage)
									hitMLG = true;
							} else if (((EntityHitResult) hit).getEntity() instanceof EnderDragonPart part)
							{
								float damage = gunshotContext.ImpactDamage();
								if (part != part.parentMob.head)
									damage = damage / 4.0F + Math.min(damage, 1.0F);
								if (part.parentMob.getHealth() <= damage)
									hitMLG = true;
							}
						}
					}
				}
			}

			// If this was my shot, and it hit, hit marker me
			if(hitEntity && context.Shooter().IsLocalPlayerOwner())
			{
				FlansModClient.CLIENT_OVERLAY_HOOKS.ApplyHitMarker(hitMLG ? 100.0f : 10.0f, hitMLG);
			}
		}
	}

	@Override
	public void OnTickClient(ActionGroupContext context)
	{
		super.OnTickClient(context);
		int tickAfter = GetProgressTicks();
		int tickBefore = tickAfter - 1;

		boolean playedASoundThisTick = false;

		ParticleEngine particleEngine = Minecraft.getInstance().particleEngine;
		ActionDefinition shootActionDef = context.GetShootActionDefinition();

		for(GunshotCollection shotCollection : Results.values())
		{
			for (Gunshot shot : shotCollection.Shots)
			{
				double t0 = shot.fromShotIndex * context.RepeatDelayTicks();
				GunshotContext gunshotContext = GunshotContext.CreateFrom(context, shot.bulletDef);
				for (HitResult hit : shot.hits)
				{
					// Check if this hit should be processed on this frame
					double t = Maths.CalculateParameter(shot.origin, shot.Endpoint(), hit.getLocation()) * GetDurationPerTriggerTicks() + t0;
					if (tickBefore <= t && t < tickAfter)
					{
						// Create hit particles
						switch (hit.getType())
						{
							case BLOCK -> {
								ClientLevel level = Minecraft.getInstance().level;
								BlockHitResult blockHit = (BlockHitResult) hit;
								if (shootActionDef != null && gunshotContext.Bullet.shootStats.impact.decal != null
									&& gunshotContext.Bullet.shootStats.impact.decal.length() > 0)
								{
									FlansModClient.DECAL_RENDERER.AddDecal(
										ResourceLocation.tryParse(gunshotContext.Bullet.shootStats.impact.decal + ".png").withPrefix("textures/"),
										blockHit.getLocation(),
										blockHit.getDirection(),
										level.random.nextFloat() * 360.0f,
										1000);
								}

								Vec3[] motions = new Vec3[3];
								motions[0] = Maths.Reflect(shot.trajectory.normalize(), blockHit.getDirection());
								Vec3i normal = blockHit.getDirection().getNormal();
								for (int i = 1; i < motions.length; i++)
								{
									motions[i] = new Vec3(
										normal.getX() + level.random.nextGaussian() * 0.2d,
										normal.getY() + level.random.nextGaussian() * 0.2d,
										normal.getZ() + level.random.nextGaussian() * 0.2d);
									motions[i] = motions[i].normalize().scale(0.3d);
								}

								for (int i = 0; i < motions.length; i++)
								{
									BlockState state = level.getBlockState(blockHit.getBlockPos());
									particleEngine.add(new GunshotHitBlockParticle(
										level,
										hit.getLocation().x,
										hit.getLocation().y,
										hit.getLocation().z,
										motions[i].x,
										motions[i].y,
										motions[i].z,
										state,
										blockHit.getBlockPos())
										.updateSprite(state, blockHit.getBlockPos())
										.scale(0.5f));
								}
							}
							case ENTITY -> {
								EntityHitResult entityHitResult = (EntityHitResult)hit;
								if(entityHitResult.getEntity().isAttackable())
								{
									Vec3 shotMotion = shot.trajectory.normalize().scale(GetDurationPerTriggerTicks());
									particleEngine.createParticle(
										ParticleTypes.DAMAGE_INDICATOR,
										hit.getLocation().x,
										hit.getLocation().y,
										hit.getLocation().z,
										shotMotion.x,
										shotMotion.y,
										shotMotion.z);
								}
							}
						}

						// Play a sound, only once per tick to avoid audio overload
						if (!playedASoundThisTick && gunshotContext.Bullet.shootStats.impact.hitSounds != null)
						{
							playedASoundThisTick = true;
							//Minecraft.getInstance().getSoundManager().play(actionDef.ShootStats.Impact.HitSound);
						}
					}
				}
			}
		}
	}

	public Vec3 GetPlayerMuzzlePosition(ActionGroupContext context, int nTicksAgo)
	{
		if(context.Shooter().Entity() instanceof Player player)
		{
			PlayerSnapshot snapshot = Raytracer.ForLevel(player.level).GetSnapshot(player, nTicksAgo);
			snapshot.GetMuzzlePosition();
		}
		else if(context.Shooter().Entity() instanceof LivingEntity living)
		{
			return living.getEyePosition();
		}
		return context.Shooter().Entity().getEyePosition();

		/*
		ItemStack itemstack = hand == EnumHand.OFF_HAND ? player.getHeldItemOffhand() : player.getHeldItemMainhand();

		if(itemstack.getItem() instanceof ItemGun)
		{
			GunType gunType = ((ItemGun)itemstack.getItem()).GetType();
			AttachmentType barrelType = gunType.getBarrel(itemstack);

			return Vector3f.add(new Vector3f(player.posX, player.posY, player.posZ), snapshot.GetMuzzleLocation(gunType, barrelType, hand), null);
		}
		 */
	}

	/*
	public float VerticalRecoil(GunshotContext context) { return GetGunStats(context).VerticalRecoil; }
	public float HorizontalRecoil(ActionGroupContext context) { return GetGunStats(context).HorizontalRecoil; }
	public float Spread(ActionGroupContext context) { return GetGunStats(context).Spread; }
	public float Speed(ActionGroupContext context) { return GetGunStats(context).Speed; }
	public int Count(ActionGroupContext context) { return GetGunStats(context).BulletCount; }
	public float PenetrationPower(ActionGroupContext context) { return GetGunStats(context).PenetrationPower; }

	public float BaseDamage(ActionGroupContext context) { return GetGunStats(context).BaseDamage; }
	public float Knockback(ActionGroupContext context) { return GetGunStats(context).Knockback; }
	public float MultiplierVsPlayers(ActionGroupContext context) { return GetGunStats(context).MultiplierVsPlayers; }
	public float MultiplierVsVehicles(ActionGroupContext context) { return GetGunStats(context).MultiplierVsVehicles; }
	public float SplashDamageRadius(ActionGroupContext context) { return GetGunStats(context).SplashDamageRadius; }
	public float SplashDamageFalloff(ActionGroupContext context) { return GetGunStats(context).SplashDamageFalloff; }
	public float SetFireToTarget(ActionGroupContext context) { return GetGunStats(context).SetFireToTarget; }
	public float FireSpreadRadius(ActionGroupContext context) { return GetGunStats(context).FireSpreadRadius; }
	public float FireSpreadAmount(ActionGroupContext context) { return GetGunStats(context).FireSpreadAmount; }
	public ESpreadPattern SpreadPattern(ActionGroupContext context) { return GetGunStats(context).SpreadPattern; }
	 */
}
