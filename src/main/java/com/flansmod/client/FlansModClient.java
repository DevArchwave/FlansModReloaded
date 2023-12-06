package com.flansmod.client;

import com.flansmod.client.gui.crafting.WorkbenchScreen;
import com.flansmod.client.render.ClientRenderHooks;
import com.flansmod.client.input.ClientInputHooks;
import com.flansmod.client.render.FirstPersonManager;
import com.flansmod.client.render.FlanModelRegistration;
import com.flansmod.client.render.MagazineTextureAtlas;
import com.flansmod.client.render.animation.FlanimationDefinitions;
import com.flansmod.client.render.bullets.BulletEntityRenderer;
import com.flansmod.client.render.debug.DebugRenderer;
import com.flansmod.client.render.decals.DecalRenderer;
import com.flansmod.client.render.bullets.ShotRenderer;
import com.flansmod.common.FlansMod;
import com.flansmod.common.actions.contexts.GunContextCache;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemModelShaper;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD, modid = FlansMod.MODID)
public class FlansModClient
{
	public static final ShotRenderer SHOT_RENDERER= new ShotRenderer();
	public static final DebugRenderer DEBUG_RENDERER = new DebugRenderer();
	public static final ClientInputHooks CLIENT_INPUT_HOOKS = new ClientInputHooks();
	public static final ClientRenderHooks CLIENT_OVERLAY_HOOKS = new ClientRenderHooks();
	public static final FlanModelRegistration MODEL_REGISTRATION = new FlanModelRegistration();
	public static final FlanimationDefinitions ANIMATIONS = new FlanimationDefinitions();
	public static final DecalRenderer DECAL_RENDERER = new DecalRenderer();
	public static final MagazineTextureAtlas MAGAZINE_ATLAS = new MagazineTextureAtlas();
	public static final RecoilManager RECOIL = new RecoilManager();

	public static final ClientActionManager ACTIONS_CLIENT = new ClientActionManager();
	public static final GunContextCache GUN_CONTEXTS_CLIENT = new GunContextCache();


	public static long PREV_FRAME_NS = 0L;
	public static long THIS_FRAME_NS = 0L;
	public static float FrameDeltaSeconds() { return (THIS_FRAME_NS - PREV_FRAME_NS) / 1000000000f; }
	public static float FrameAbsoluteSeconds() { return THIS_FRAME_NS / 1000000000f; }

	@Nullable
	private static ShaderInstance GUN_CUTOUT;

	@SubscribeEvent
	public static void ClientInit(final FMLClientSetupEvent event)
	{
		IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
		ACTIONS_CLIENT.HookClient(modEventBus);
		MODEL_REGISTRATION.Hook(modEventBus);
		modEventBus.register(ANIMATIONS);
		MAGAZINE_ATLAS.Init();
		InitReflection();

		// Screens
		MenuScreens.register(FlansMod.WORKBENCH_MENU.get(), WorkbenchScreen::new);

		// Entity Renderers
		EntityRenderers.register(FlansMod.ENT_TYPE_BULLET.get(), BulletEntityRenderer::new);

		MinecraftForge.EVENT_BUS.addListener(FlansModClient::RenderTick);
	}

	@SubscribeEvent
	public static void OnRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) { MODEL_REGISTRATION.OnRegisterGeometryLoaders(event); }

	@SubscribeEvent
	public static void ModelRegistryEvent(ModelEvent.RegisterAdditional event)
	{
		ItemModelShaper shaper = Minecraft.getInstance().getItemRenderer().getItemModelShaper();

		for (var entry : FlansMod.ITEMS.getEntries())
		{
			event.register(new ModelResourceLocation(entry.getId(), "inventory"));
			shaper.register(entry.get(), new ModelResourceLocation(entry.getId(), "inventory"));
		}

		for (ResourceLocation gunLoc : FlansMod.GUNS.getIds())
		{
			
		}
	}

	public static ShaderInstance GetGunCutoutShader() { return GUN_CUTOUT; }

	public static void SetMissTime(int missTime)
	{
		try
		{
			MINECRAFT_MISS_TIME.set(Minecraft.getInstance(), missTime);
		}
		catch (Exception e)
		{
			FlansMod.LOGGER.error("Failed to SetMissTime due to " + e);
		}
	}



	@SubscribeEvent
	public static void ShaderRegistryEvent(RegisterShadersEvent event)
	{
		try
		{
			event.registerShader(new ShaderInstance(event.getResourceProvider(), "flansmod:rendertype_flans_gun_cutout", DefaultVertexFormat.BLOCK), (shader) -> {
				GUN_CUTOUT = shader;
			});
		}
		catch(Exception ignored)
		{

		}
	}

	public static void RenderTick(TickEvent.RenderTickEvent event)
	{
		PREV_FRAME_NS = THIS_FRAME_NS;
		THIS_FRAME_NS = Util.getNanos();

		FirstPersonManager.RenderTick();
	}

	@SubscribeEvent
	public static void RegisterClientReloadListeners(RegisterClientReloadListenersEvent event)
	{
		event.registerReloadListener(MAGAZINE_ATLAS);
		event.registerReloadListener(ANIMATIONS);

		FlansMod.RegisterCommonReloadListeners(event::registerReloadListener);
	}

	// ---------------------------
	// REFLECTION
	// ---------------------------
	private static final Field MINECRAFT_MISS_TIME = ObfuscationReflectionHelper.findField(Minecraft.class, "f_91078_");
	private static void InitReflection()
	{
		MINECRAFT_MISS_TIME.setAccessible(true);
	}

}
