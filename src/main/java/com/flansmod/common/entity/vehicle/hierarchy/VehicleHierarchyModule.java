package com.flansmod.common.entity.vehicle.hierarchy;

import com.flansmod.common.FlansMod;
import com.flansmod.common.entity.ITransformChildEntity;
import com.flansmod.common.entity.ITransformPair;
import com.flansmod.common.entity.vehicle.*;
import com.flansmod.common.entity.vehicle.controls.ForceModel;
import com.flansmod.common.entity.vehicle.damage.VehicleDamageModule;
import com.flansmod.common.entity.vehicle.damage.VehicleHitResult;
import com.flansmod.common.network.FlansEntityDataSerializers;
import com.flansmod.common.types.vehicles.VehicleDefinition;
import com.flansmod.common.types.vehicles.elements.ArticulatedPartDefinition;
import com.flansmod.util.Maths;
import com.flansmod.util.Transform;
import com.flansmod.util.TransformStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.util.TriConsumer;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class VehicleHierarchyModule implements IVehicleModule
{
	public static final EntityDataAccessor<PerPartMap<ArticulationSyncState>> ARTICULATIONS_ACCESSOR =
		SynchedEntityData.defineId(VehicleEntity.class, FlansEntityDataSerializers.ARTICULATION_MAP);

	@Nonnull
	public final VehicleDefinitionHierarchy Reference;
	@Nullable
	public final VehicleDefinitionHierarchy.Node RootDefinitionNode;
	@Nonnull
	public final Map<String, ArticulatedPartDefinition> ArticulatedParts = new HashMap<>();
	@Nonnull
	public Transform RootTransform = Transform.Identity();
	@Nonnull
	public Transform RootTransformPrev = Transform.Identity();
	@Nonnull
	private final SynchedEntityData VehicleDataSynchronizer;

	public VehicleHierarchyModule(@Nonnull VehicleDefinitionHierarchy hierarchy,
								  @Nonnull VehicleEntity vehicle)
	{
		Reference = hierarchy;
		RootDefinitionNode = hierarchy.Find("body");

		hierarchy.ForEachArticulation(ArticulatedParts::put);
		VehicleDataSynchronizer = vehicle.getEntityData();
	}

	// Root Transform settings
	public void SetPosition(double x, double y, double z) { RootTransform = RootTransform.WithPosition(x, y, z); }
	public void SetPosition(@Nonnull Vec3 pos) { RootTransform = RootTransform.WithPosition(pos); }
	public void SetYaw(float yaw) { RootTransform = RootTransform.WithYaw(yaw); }
	public void SetPitch(float pitch) { RootTransform = RootTransform.WithPitch(pitch); }
	public void SetRoll(float roll) { RootTransform = RootTransform.WithRoll(roll); }
	public void RotateYaw(float yaw) { RootTransform = RootTransform.RotateYaw(yaw); }
	public void RotatePitch(float pitch) { RootTransform = RootTransform.RotatePitch(pitch); }
	public void RotateRoll(float roll) { RootTransform = RootTransform.RotateRoll(roll); }
	public void SetEulerAngles(float pitch, float yaw, float roll) { RootTransform = RootTransform.WithEulerAngles(pitch, yaw, roll); }
	public void SetCurrentRootTransform(@Nonnull Transform transform) { RootTransform = transform; }
	public void SetPreviousRootTransform(@Nonnull Transform transform) { RootTransformPrev = transform; }


	// -----------------------------------------------------------------------------------------------
	// Synced data maps
	@Nonnull private PerPartMap<ArticulationSyncState> GetArticulationMap() { return VehicleDataSynchronizer.get(ARTICULATIONS_ACCESSOR); }
	private void SetArticulationMap(@Nonnull PerPartMap<ArticulationSyncState> map) { VehicleDataSynchronizer.set(ARTICULATIONS_ACCESSOR, map); }

	// -----------------------------------------------------------------------------------------------
	// Velocity is units per second, NOT per tick
	// Articulation Accessors
	public void SetArticulationParameterByHash(int hash, float parameter)
	{
		PerPartMap<ArticulationSyncState> map = GetArticulationMap();
		float existingVelocity = map.Values.containsKey(hash) ? map.Values.get(hash).Velocity() : 0.0f;
		map.Values.put(hash, new ArticulationSyncState(parameter, existingVelocity));
		SetArticulationMap(map);
	}
	public void SetArticulationVelocityByHash(int hash, float velocity)
	{
		PerPartMap<ArticulationSyncState> map = GetArticulationMap();
		float existingParam = map.Values.containsKey(hash) ? map.Values.get(hash).Parameter() : 0.0f;
		map.Values.put(hash, new ArticulationSyncState(existingParam, velocity));
		SetArticulationMap(map);
	}
	public float GetArticulationParameterByHash(int hash)
	{
		PerPartMap<ArticulationSyncState> map = GetArticulationMap();
		return map.Values.containsKey(hash) ? map.Values.get(hash).Parameter() : 0.0f;
	}
	public float GetArticulationVelocityByHash(int hash)
	{
		PerPartMap<ArticulationSyncState> map = GetArticulationMap();
		return map.Values.containsKey(hash) ? map.Values.get(hash).Velocity() : 0.0f;
	}

	public void SetArticulationParameter(@Nonnull String partName, float parameter)
	{
		SetArticulationParameterByHash(partName.hashCode(), parameter);
	}
	public float GetArticulationParameter(@Nonnull String partName)
	{
		return GetArticulationParameterByHash(partName.hashCode());
	}
	public void SetArticulationVelocity(@Nonnull String partName, float velocity)
	{
		SetArticulationVelocityByHash(partName.hashCode(), velocity);
	}
	public float GetArticulationVelocity(@Nonnull String partName)
	{
		return GetArticulationVelocityByHash(partName.hashCode());
	}
	@Nonnull
	public Transform GetArticulationTransform(@Nonnull String partName)
	{
		ArticulatedPartDefinition def = ArticulatedParts.get(partName);
		if (def != null && def.active)
		{
			float parameter = GetArticulationParameter(partName);
			return def.Apply(parameter);
		}
		return Transform.IDENTITY;
	}


	// ----------------------------------------------------------------------------------------------------------------
	// World to Part
	@Nonnull public ITransformPair GetWorldToPart(@Nonnull String apPath) { return ITransformPair.compose(GetWorldToRoot(), GetRootToPart(apPath)); }
	@Nonnull public Transform GetWorldToPartPrevious(@Nonnull String apPath) { return GetWorldToPart(apPath).GetPrevious(); }
	@Nonnull public Transform GetWorldToPartCurrent(@Nonnull String apPath) { return GetWorldToPart(apPath).GetCurrent(); }

	// ----------------------------------------------------------------------------------------------------------------
	// World to Root
	@Nonnull public ITransformPair GetWorldToRoot() { return ITransformPair.of(this::GetWorldToRootPrevious, this::GetWorldToRootCurrent); }
	@Nonnull public Transform GetWorldToRootPrevious() { return RootTransformPrev; }
	@Nonnull public Transform GetWorldToRootCurrent() { return RootTransform; }

	// ----------------------------------------------------------------------------------------------------------------
	// Root to Part
	@Nonnull public ITransformPair GetRootToPart(@Nonnull String vehiclePart) {
		return ITransformPair.of(() -> GetRootToPartPrevious(vehiclePart), () -> GetRootToPartCurrent(vehiclePart));
	}
	@Nonnull public Transform GetRootToPartPrevious(@Nonnull String vehiclePart) {
		TransformStack stack = new TransformStack();
		TransformRootToPartPrevious(vehiclePart, stack);
		return stack.Top();
	}
	@Nonnull public Transform GetRootToPartCurrent(@Nonnull String vehiclePart) {
		TransformStack stack = new TransformStack();
		TransformRootToPartCurrent(vehiclePart, stack);
		return stack.Top();
	}
	public void TransformRootToPartPrevious(@Nonnull String vehiclePart, @Nonnull TransformStack stack) {
		Reference.Traverse(vehiclePart, (node) -> { stack.add(GetPartLocalPrevious(node)); });
	}
	public void TransformRootToPartCurrent(@Nonnull String vehiclePart, @Nonnull TransformStack stack) {
		Reference.Traverse(vehiclePart, (node) -> { stack.add(GetPartLocalCurrent(node)); });
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Part to Part
	@Nonnull public ITransformPair GetPartLocal(@Nonnull VehicleDefinitionHierarchy.Node node) {
		return ITransformPair.of(() -> GetPartLocalPrevious(node), () -> GetPartLocalCurrent(node));
	}
	@Nonnull
	public Transform GetPartLocalPrevious(@Nonnull VehicleDefinitionHierarchy.Node node)
	{
		if(node.Def.IsArticulated())
		{
			float articulationParameter = GetArticulationParameter(node.Def.partName);
			float articulationVelocity = GetArticulationVelocity(node.Def.partName);
			return node.Def.articulation.Apply(articulationParameter - articulationVelocity);
		}
		return node.Def.LocalTransform.get();
	}
	@Nonnull
	public Transform GetPartLocalCurrent(@Nonnull VehicleDefinitionHierarchy.Node node)
	{
		if(node.Def.IsArticulated())
		{
			float articulationParameter = GetArticulationParameter(node.Def.partName);
			return node.Def.articulation.Apply(articulationParameter);
		}
		return node.Def.LocalTransform.get();
	}

	// ---------------------------------------------------------------------------------------------------------
	// AABBs
	public void ForEachCollider(@Nonnull TriConsumer<String, ITransformPair, List<AABB>> func)
	{
		if (RootDefinitionNode != null)
		{
			// Process the root node as a single collider
			ForCollidersAttachedTo(RootDefinitionNode, (trans, list) -> func.accept(VehicleDefinition.CoreName, trans, list));

			// Then process each dynamic child
			RootDefinitionNode.ForEachNode(false, false, true, (dynamicChild) ->
			{
				ForCollidersAttachedTo(dynamicChild, (trans, list) -> func.accept(dynamicChild.Path(), trans, list));
			});
		}
	}
	public void ForCollidersAttachedTo(@Nonnull VehicleDefinitionHierarchy.Node node, @Nonnull BiConsumer<ITransformPair, List<AABB>> func)
	{
		List<AABB> bbs = new ArrayList<>();
		node.ForEachNode(true, true, false, (staticChild) -> {
			if(staticChild.Def.IsDamageable())
			{
				bbs.add(staticChild.Def.damage.Hitbox.get());
			}
		});
		func.accept(GetRootToPart(node.Path()), bbs);
	}

	// ---------------------------------------------------------------------------------------------------------
	// Raycasts
	// Start and end should be relative to the root node
	public void Raycast(@Nonnull VehicleEntity vehicle,
						@Nonnull Vec3 start,
						@Nonnull Vec3 end,
						@Nonnull List<HitResult> results,
						float dt)
	{
		if(RootDefinitionNode != null)
		{
			TransformStack stack = TransformStack.of(GetWorldToRoot().GetDelta(dt));
			Raycast(vehicle, stack, RootDefinitionNode, start, end, results, dt);
		}
	}
	private void Raycast(@Nonnull VehicleEntity vehicle, @Nonnull TransformStack stack, @Nonnull VehicleDefinitionHierarchy.Node node, @Nonnull Vec3 start, @Nonnull Vec3 end, @Nonnull List<HitResult> results, float dt)
	{
		stack.PushSaveState();

		// If this piece is articulated, add a transform
		if(node.Def.IsArticulated())
		{
			Transform articulation = GetPartLocal(node).GetDelta(dt);
			if(!articulation.IsIdentity())
			{
				start = articulation.GlobalToLocalPosition(start);
				end = articulation.GlobalToLocalPosition(end);
				stack.add(articulation);
			}
		}

		// Cast against children nodes
		for(VehicleDefinitionHierarchy.Node child : node.StaticChildren.values())
		{
			Raycast(vehicle, stack, child, start, end, results, dt);
		}
		for(VehicleDefinitionHierarchy.Node child : node.DynamicChildren.values())
		{
			Raycast(vehicle, stack, child, start, end, results, dt);
		}

		// If this piece has a hitbox (needs damageable), cast against it
		if(node.Def.IsDamageable())
		{
			stack.PushSaveState();
			stack.add(Transform.FromPos(node.Def.damage.hitboxCenter));
			Vector3d hitPos = new Vector3d();
			if(Maths.RayBoxIntersect(start, end, stack.Top(), node.Def.damage.hitboxHalfExtents.toVector3f(), hitPos))
			{
				results.add(new VehicleHitResult(vehicle, node.Path()));
			}
			stack.PopSaveState();
		}

		stack.PopSaveState();
	}


	@Override
	public void Tick(@Nonnull VehicleEntity vehicle)
	{

	}

	@Override
	public void Load(@Nonnull VehicleEntity vehicle, @Nonnull CompoundTag tags)
	{
		PerPartMap<ArticulationSyncState> articulation = GetArticulationMap();
		for(String key : tags.getAllKeys())
		{
			CompoundTag articulationTags = tags.getCompound(key);
			if(articulation.Values.containsKey(key.hashCode()))
			{
				articulation.Values.put(key.hashCode(), new ArticulationSyncState(articulationTags.getFloat("param"), articulationTags.getFloat("velocity")));
			}
			else FlansMod.LOGGER.warn("Articulation key " + key + " was stored in vehicle save data, but this vehicle doesn't have that part");
		}
	}

	@Nonnull
	@Override
	public CompoundTag Save(@Nonnull VehicleEntity vehicle)
	{
		PerPartMap<ArticulationSyncState> map = GetArticulationMap();
		CompoundTag tags = new CompoundTag();
		for(var kvp : ArticulatedParts.entrySet())
		{
			CompoundTag articulationTags = new CompoundTag();
			articulationTags.putFloat("param", GetArticulationParameter(kvp.getKey()));
			articulationTags.putFloat("velocity", GetArticulationVelocity(kvp.getKey()));
			tags.put(kvp.getKey(), articulationTags);
		}
		return tags;
	}
}
