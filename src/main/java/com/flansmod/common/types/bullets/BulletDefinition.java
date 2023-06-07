package com.flansmod.common.types.bullets;

import com.flansmod.common.FlansMod;
import com.flansmod.common.actions.Action;
import com.flansmod.common.types.JsonDefinition;
import com.flansmod.common.types.JsonField;
import com.flansmod.common.types.elements.ActionDefinition;
import com.flansmod.common.types.elements.ShotDefinition;
import net.minecraft.resources.ResourceLocation;

public class BulletDefinition extends JsonDefinition
{
	public static final BulletDefinition INVALID = new BulletDefinition(new ResourceLocation(FlansMod.MODID, "bullets/null"));
	public static final String TYPE = "bullet";
	public static final String FOLDER = "bullets";

	@Override
	public String GetTypeName() { return TYPE; }

	public BulletDefinition(ResourceLocation resLoc)
	{
		super(resLoc);
	}

	@JsonField
	public float gravityFactor = 0.25f;
	@JsonField
	public int maxStackSize = 64;
	@JsonField
	public int roundsPerItem = 1;
	@JsonField
	public ShotDefinition shootStats = new ShotDefinition();

	@JsonField
	public ActionDefinition[] onShootActions = new ActionDefinition[0];
	@JsonField
	public ActionDefinition[] onClipEmptyActions = new ActionDefinition[0];
	@JsonField
	public ActionDefinition[] onReloadActions = new ActionDefinition[0];

}
