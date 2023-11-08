package com.flansmod.common.types.elements;

import com.flansmod.common.types.JsonField;
import net.minecraft.world.phys.Vec3;

public class AttachmentSettingsDefinition
{
	@JsonField
	public String[] allowlist = new String[0];
	@JsonField
	public boolean allowAll = true;

	@JsonField
	public int numAttachmentSlots = 0;

	@JsonField
	public boolean hideDefaultMesh = true;
}
