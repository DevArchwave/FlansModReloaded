package com.flansmod.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.flansmod.client.FlansModClient;
import com.flansmod.client.render.animation.AnimationDefinition;
import com.flansmod.client.render.animation.ESmoothSetting;
import com.flansmod.client.render.animation.elements.KeyframeDefinition;
import com.flansmod.client.render.animation.elements.PoseDefinition;
import com.flansmod.client.render.animation.elements.SequenceDefinition;
import com.flansmod.client.render.animation.elements.SequenceEntryDefinition;
import com.flansmod.client.render.guns.AttachmentItemRenderer;
import com.flansmod.client.render.models.*;
import com.flansmod.common.FlansMod;
import com.flansmod.common.actions.*;
import com.flansmod.common.gunshots.GunContextPlayer;
import com.flansmod.common.gunshots.ShooterContext;
import com.flansmod.common.item.FlanItem;
import com.flansmod.common.types.attachments.EAttachmentType;
import com.flansmod.util.Maths;
import com.flansmod.util.MinecraftHelpers;
import com.flansmod.util.Transform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class FlanItemModelRenderer extends BlockEntityWithoutLevelRenderer
{
    protected static final RenderStateShard.ShaderStateShard GUN_CUTOUT_SHADER = new RenderStateShard.ShaderStateShard(FlansModClient::GetGunCutoutShader);
    private static class RenderTypeFlanItem extends RenderType {
        protected static final Function<ResourceLocation, RenderType> GUN_CUTOUT = Util.memoize((p_173204_) -> {
            RenderType.CompositeState rendertype$compositestate =
                RenderType.CompositeState.builder()
                    .setShaderState(GUN_CUTOUT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(p_173204_, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(CULL)
                    .setOverlayState(OVERLAY)
                    .setLightmapState(LIGHTMAP)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(true);
            return create("flan_gun_item",
                DefaultVertexFormat.BLOCK,
                VertexFormat.Mode.QUADS,
                256,
                true,
                false,
                rendertype$compositestate);
        });

        public RenderTypeFlanItem(String p_173178_, VertexFormat p_173179_, VertexFormat.Mode p_173180_, int p_173181_, boolean p_173182_, boolean p_173183_, Runnable p_173184_, Runnable p_173185_)
        {
            super(p_173178_, p_173179_, p_173180_, p_173181_, p_173182_, p_173183_, p_173184_, p_173185_);
        }
    }
    public static RenderType flanItemRenderType(ResourceLocation texture)
    {
        if(texture == null)
            texture = MissingTextureAtlasSprite.getLocation();
        return RenderTypeFlanItem.GUN_CUTOUT.apply(texture);
    }


    protected TurboRig UnbakedRig;
    protected TurboRig.Baked BakedRig;
    public final boolean ShouldRenderWhenHeld;

    public FlanItemModelRenderer(boolean shouldRenderWhenHeld)
    {
        super(null, null);
        ShouldRenderWhenHeld = shouldRenderWhenHeld;
    }

    protected abstract void DoRender(@Nullable Entity heldByEntity, @Nullable ItemStack stack, @Nonnull RenderContext renderContext);

    @Override
    public void renderByItem(@Nullable ItemStack stack,
                             @Nonnull ItemTransforms.TransformType transformType,
                             @Nonnull PoseStack ms,
                             @Nonnull MultiBufferSource buffers,
                             int light,
                             int overlay)
    {
        RenderSystem.enableDepthTest();
        ms.pushPose();
        RenderItem(null, transformType, stack, ms, buffers, light, overlay);
        ms.popPose();
    }

    public void RenderFirstPerson(Entity entity,
                                  ItemStack stack,
                                  HumanoidArm arm,
                                  ItemTransforms.TransformType transformType,
                                  PoseStack ms,
                                  MultiBufferSource buffers,
                                  int light,
                                  int overlay,
                                  float equipProgress)
    {
        if(BakedRig == null)
            return;

        RenderItem(entity, transformType, stack, ms, buffers, light, overlay);
    }

    public void RenderDirect(@Nullable Entity heldByEntity, @Nullable ItemStack stack, @Nonnull RenderContext renderContext)
    {
        renderContext.Poses.pushPose();
        {
            // Apply root transform
            if(renderContext.TransformType != null)
                BakedRig.ApplyTransform(renderContext.TransformType, renderContext.Poses, false);

            DoRender(heldByEntity, stack, renderContext);
        }
        renderContext.Poses.popPose();
    }

    protected void RenderItem(@Nullable Entity entity,
                              @Nonnull ItemTransforms.TransformType transformType,
                              @Nullable ItemStack stack,
                              @Nonnull PoseStack requestedPoseStack,
                              @Nonnull MultiBufferSource buffers,
                              int light,
                              int overlay)
    {
        requestedPoseStack.pushPose();
        {
            FlanItem flanItem = stack != null ? (stack.getItem() instanceof FlanItem ? (FlanItem)stack.getItem() : null) : null;
            String skin = "default";
            if(flanItem != null)
                skin = flanItem.GetPaintjobName(stack);

            boolean shouldRenderRig = true;
            if(transformType == ItemTransforms.TransformType.GUI)
            {
                BakedModel iconModel = BakedRig.GetIconModel(skin);
                if(iconModel != null)
                {
                    shouldRenderRig = false;
                    requestedPoseStack.setIdentity();
                    requestedPoseStack.translate(-0.5f, -0.5f, 0f);
                    Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                        requestedPoseStack.last(),
                        buffers.getBuffer(Sheets.cutoutBlockSheet()),
                        null,
                        iconModel,
                        1f, 1f, 1f,
                        light,
                        overlay);
                }
            }

            if(shouldRenderRig)
            {
                if(transformType.firstPerson())
                {
                    Transform adsBlendTransform = FirstPersonManager.GetFirstPersonRenderPos(stack, transformType);
                    requestedPoseStack.translate(adsBlendTransform.position.x, adsBlendTransform.position.y, adsBlendTransform.position.z);
                    requestedPoseStack.mulPose(adsBlendTransform.orientation);
                }
                else
                {
                    BakedRig.ApplyTransform(transformType, requestedPoseStack, false);
                }

                // Bind texture
                ResourceLocation texture = BakedRig.GetTexture(skin);

                // Find the right buffer
                VertexConsumer vc = buffers.getBuffer(flanItemRenderType(texture));

                // Render item
                DoRender(entity, stack, new RenderContext(buffers, transformType, requestedPoseStack, light, overlay));
            }
        }
        requestedPoseStack.popPose();
    }

    protected void ApplyAnimations(RenderContext renderContext, AnimationDefinition animationSet, ActionStack actionStack, String partName)
    {
        if(UnbakedRig == null)
            return;

        if(actionStack != null)
        {
            List<AnimationAction> animActions = new ArrayList<>();
            for(ActionGroupInstance group : actionStack.GetActiveActionGroups())
                for(ActionInstance action : group.GetActions())
                {
                    if (action instanceof AnimationAction animAction)
                        animActions.add(animAction);
                }

            List<Transform> poses = new ArrayList<>();
            for (AnimationAction animAction : animActions)
            {

                SequenceDefinition sequence = animationSet.GetSequence(animAction.Def.anim);
                if (sequence == null)
                {
                    FlansMod.LOGGER.warn("Could not find animation sequence " + animAction.Def.anim + " in anim set " + animationSet.Location);
                    continue;
                }

                // Make sure we scale the sequence (which can be played at any speed) with the target duration of this specific animation action
                float progress = animAction.AnimFrame + Minecraft.getInstance().getPartialTick();
                float animMultiplier = sequence.Duration() / (animAction.Def.duration * 20f);
                progress *= animMultiplier;

                // Find the segment of this animation that we need
                SequenceEntryDefinition[] segment = sequence.GetSegment(progress);
                float segmentDuration = segment[1].tick - segment[0].tick;

                // If it is valid, let's animate it
                if (segmentDuration >= 0.0f)
                {
                    KeyframeDefinition from = animationSet.GetKeyframe(segment[0]);
                    KeyframeDefinition to = animationSet.GetKeyframe(segment[1]);
                    if (from != null && to != null)
                    {
                        float linearParameter = (progress - segment[0].tick) / segmentDuration;
                        linearParameter = Maths.Clamp(linearParameter, 0f, 1f);
                        float outputParameter = linearParameter;

                        // Instant transitions take priority first
                        if (segment[0].exit == ESmoothSetting.instant)
                            outputParameter = 1.0f;
                        if (segment[1].entry == ESmoothSetting.instant)
                            outputParameter = 0.0f;

                        // Then apply smoothing?
                        if (segment[0].exit == ESmoothSetting.smooth)
                        {
                            // Smoothstep function
                            if (linearParameter < 0.5f)
                                outputParameter = linearParameter * linearParameter * (3f - 2f * linearParameter);
                        }
                        if (segment[1].entry == ESmoothSetting.smooth)
                        {
                            // Smoothstep function
                            if (linearParameter > 0.5f)
                                outputParameter = linearParameter * linearParameter * (3f - 2f * linearParameter);
                        }

                        PoseDefinition fromPose = animationSet.GetPoseForPart(from, partName);
                        PoseDefinition toPose = animationSet.GetPoseForPart(to, partName);

                        Vector3f pos = PoseDefinition.LerpPosition(UnbakedRig.GetFloatParams(), fromPose, toPose, outputParameter);
                        Quaternionf rotation = PoseDefinition.LerpRotation(UnbakedRig.GetFloatParams(), fromPose, toPose, outputParameter);

                        Transform test = new Transform(pos, rotation);
                        poses.add(test);
                    }
                }
            }

           if(poses.size() > 0)
           {
               Transform resultPose = Transform.Interpolate(poses);
               renderContext.Poses.translate(resultPose.position.x, resultPose.position.y, resultPose.position.z);
               renderContext.Poses.mulPose(resultPose.orientation);
           }

            // Apply the model offset after animating
            if(UnbakedRig != null)
            {
                TurboModel model = UnbakedRig.GetPart(partName);
                if (model != null)
                {
                    renderContext.Poses.translate(model.offset.x, model.offset.y, model.offset.z);
                }
            }
        }
    }

    private void ApplyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equipProgress)
    {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate((float)i * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }

    protected Transform GetEyeLine(ItemTransforms.TransformType transformType)
    {
        if(UnbakedRig == null)
            return Transform.Identity();

        List<Transform> otherEyeLines = new ArrayList<>();
        List<Transform> myEyeLines = new ArrayList<>();
        ShooterContext shooterContext = ShooterContext.GetOrCreate(Minecraft.getInstance().player);
        if(shooterContext.IsValid())
        {
            for(GunContext gunContext : shooterContext.GetAllActiveGunContexts())
            {
                if(gunContext.IsValid() && gunContext instanceof GunContextPlayer gunContextPlayer)
                {
                    boolean isThisHand = MinecraftHelpers.GetHand(transformType) == gunContextPlayer.GetHand();
                    for(ActionGroupInstance groupInstance : gunContext.GetActionStack().GetActiveActionGroups())
                    {
                        boolean hasADS = false;
                        for(ActionInstance actionInstance : groupInstance.GetActions())
                        {
                            if(actionInstance instanceof AimDownSightAction adsAction)
                            {
                                hasADS = true;
                            }
                        }

                        if(hasADS)
                        {
                            if(groupInstance.Context.IsAttachment())
                            {
                                EAttachmentType attachmentType = groupInstance.Context.GetAttachmentType();
                                int attachmentIndex = groupInstance.Context.GetAttachmentIndex();

                                TurboRig.AttachPoint ap = UnbakedRig.GetAttachPoint(attachmentType, attachmentIndex);
                                Transform apTransform = Transform.FromPosAndEuler(ap.Offset, ap.Euler);
                                while(!ap.AttachTo.equals("body"))
                                {
                                    TurboRig.AttachPoint parentAP = UnbakedRig.GetAttachPoint(ap.AttachTo);
                                    if(parentAP == null)
                                        break;

                                    apTransform = apTransform.RightMultiply(Transform.FromPosAndEuler(ap.Offset, ap.Euler));
                                    ap = parentAP;
                                }

                                ItemStack attachmentStack = gunContext.GetAttachmentStack(attachmentType, attachmentIndex);
                                FlanItemModelRenderer attachmentRenderer = FlansModClient.MODEL_REGISTRATION.GetModelRenderer(attachmentStack);
                                if (attachmentRenderer instanceof AttachmentItemRenderer attachmentItemRenderer)
                                {
                                    if(attachmentRenderer.UnbakedRig != null)
                                    {
                                        TurboRig.AttachPoint eyeLineAPOnAttachment = attachmentRenderer.UnbakedRig.GetAttachPoint("eye_line");
                                        if(eyeLineAPOnAttachment != null)
                                        {
                                            Transform eyeLineTransform = Transform.FromPosAndEuler(eyeLineAPOnAttachment.Offset, eyeLineAPOnAttachment.Euler);
                                            if (isThisHand)
                                                myEyeLines.add(apTransform.RightMultiply(eyeLineTransform));
                                            else
                                                otherEyeLines.add(apTransform.RightMultiply(eyeLineTransform));
                                        }
                                    }
                                }
                            }
                            else
                            {
                                TurboRig.AttachPoint ap = UnbakedRig.GetAttachPoint("eye_line");
                                if(ap != null)
                                {
                                    Transform eyeLineTransform = Transform.FromPosAndEuler(ap.Offset, ap.Euler);
                                    if (isThisHand)
                                        myEyeLines.add(eyeLineTransform);
                                    else
                                        otherEyeLines.add(eyeLineTransform);
                                }
                            }
                        }
                    }
                }
            }
        }

        if(myEyeLines.size() > 0)
        {
            Vector3f srcOffset = UnbakedRig.GetTransforms(transformType).translation;
            boolean leftHanded = transformType == ItemTransforms.TransformType.FIRST_PERSON_LEFT_HAND;
            Vector3d returnToCenter = new Vector3d(0f, -srcOffset.y*16f, leftHanded ? 8f : -8f);

            Vector3d targetDelta = returnToCenter.sub(myEyeLines.get(0).position);
            Quaternionf targetRotation = myEyeLines.get(0).orientation;

            if(otherEyeLines.size() > 0)
            {
                // Both guns are ADS, go halfway
                targetDelta.mul(0.5f);
            }

            return new Transform(targetDelta, targetRotation);
        }

        return Transform.Identity();
    }

    public ResourceLocation GetSkin(@Nullable ItemStack stack)
    {
        String skin = "default";
        if(stack != null && stack.getItem() instanceof FlanItem flanItem)
        {
            skin = flanItem.GetPaintjobName(stack);
        }
        return BakedRig.GetTexture(skin);
    }

    public void OnUnbakedModelLoaded(TurboRig unbaked)
    {
        UnbakedRig = unbaked;
    }

    public void OnBakeComplete(TurboRig.Baked baked)
    {
        BakedRig = baked;
    }

    protected void RenderFirstPersonArm(PoseStack poseStack)
    {
        //if(partName.equals("rightHand") || partName.equals("leftHand"))
        //{
        //    ResourceLocation skinLocation = Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(Minecraft.getInstance().getUser().getGameProfile());
        //    RenderSystem.setShaderTexture(0, skinLocation);
        //}
    }

    protected void RenderPartIteratively(RenderContext renderContext,
                                         String partName,
                                         Function<String, ResourceLocation> textureFunc,
                                         BiFunction<String, RenderContext, Boolean> preRenderFunc,
                                         BiConsumer<String, RenderContext> postRenderFunc)
    {
        renderContext.Poses.pushPose();
        {
            boolean shouldRender = preRenderFunc.apply(partName, renderContext);
            if(shouldRender)
            {
                RenderPartTexturedSolid(partName, textureFunc.apply(partName), renderContext);
                if(UnbakedRig != null)
                {
                    for (var kvp : UnbakedRig.GetAttachmentPoints())
                    {
                        if (kvp.getValue().AttachTo.equals(partName))
                        {
                            renderContext.Poses.pushPose();
                            renderContext.Poses.translate(kvp.getValue().Offset.x, kvp.getValue().Offset.y, kvp.getValue().Offset.z);
                            RenderPartIteratively(renderContext, kvp.getKey(), textureFunc, preRenderFunc, postRenderFunc);
                            renderContext.Poses.popPose();
                        }
                    }
                }
            }
            postRenderFunc.accept(partName, renderContext);
        }
        renderContext.Poses.popPose();
    }

    protected void RenderPartTexturedSolid(String partName, ResourceLocation withTexture, RenderContext renderContext)
    {
        VertexConsumer vc = renderContext.Buffers.getBuffer(flanItemRenderType(withTexture));
        if(UnbakedRig != null)
        {
            TurboModel unbaked = UnbakedRig.GetPart(partName);
            if (unbaked != null)
            {
                TurboRenderUtility.Render(unbaked, vc, renderContext.Poses, renderContext.Light, renderContext.Overlay);
            }
        }
    }

    @Nullable
    public TurboRig.AttachPoint GetAttachPoint(String apName)
    {
        if(UnbakedRig != null)
            return UnbakedRig.GetAttachPoint(apName.toLowerCase());
        return null;
    }
    public void GetAttachPointTree(String apName, List<TurboRig.AttachPoint> apList)
    {
        TurboRig.AttachPoint ap = GetAttachPoint(apName);
        if(ap != null)
        {
            apList.add(ap);
            if(ap.AttachTo.length() > 0 && !ap.AttachTo.equals("body") && !ap.AttachTo.equals("none"))
                GetAttachPointTree(ap.AttachTo, apList);
        }
    }
    public Transform GetDefaultTransform(String apName)
    {
        TurboRig.AttachPoint ap = GetAttachPoint(apName);
        if (ap != null)
        {
            if(ap.AttachTo.length() > 0 && !ap.AttachTo.equals("body") && !ap.AttachTo.equals("none"))
            {
                Transform childTransform = GetDefaultTransform(ap.AttachTo);
                return Transform.FromPosAndEuler(ap.Offset, ap.Euler).RightMultiply(childTransform);
            }
            return Transform.FromPosAndEuler(ap.Offset, ap.Euler);
        }
        return Transform.Identity();

    }
    public Transform GetDefaultTransform(EAttachmentType attachmentType, int attachmentIndex)
    {
        if(attachmentIndex == 0)
        {
            TurboRig.AttachPoint ap = GetAttachPoint(attachmentType.toString());
            if (ap != null)
            {
                return GetDefaultTransform(attachmentType.toString());
            }
        }

        return GetDefaultTransform(attachmentType + "_" + attachmentIndex);
    }

   //public Vector3f GetAttachPoint(String apName)
   //{
   //    if(UnbakedRig != null)
   //    {
   //        TurboRig.AttachPoint ap = UnbakedRig.GetAttachPoint(apName);
   //        if(ap != null)
   //            return ap.Offset;
   //    }
   //    return new Vector3f();
   //}
}
