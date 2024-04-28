package com.flansmod.common.entity.vehicle.hierarchy;

import com.flansmod.common.entity.vehicle.IVehicleSaveNode;
import com.flansmod.common.entity.vehicle.VehicleEntity;
import com.flansmod.common.types.vehicles.elements.ArticulatedPartDefinition;
import com.flansmod.util.Maths;
import com.flansmod.util.Transform;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nonnull;

public class VehicleArticulationSaveState implements IVehicleSaveNode
{
	@Nonnull
	public final ArticulatedPartDefinition Def;
	public float Parameter;
	public float Velocity;

	public VehicleArticulationSaveState(@Nonnull ArticulatedPartDefinition def)
	{
		Def = def;
		Parameter = def.startParameter;
		Velocity = 0.0f;
	}


	public void SetVelocity(float f) { Velocity = f; }
	public float GetVelocityUnitsPerSecond() { return Velocity; }
	public float GetVelocityUnitsPerTick() { return Velocity / 20f; }
	public float GetParameter0() { return Parameter; }
	public float GetParameter(float dt) {
		return Maths.Clamp(Parameter + GetVelocityUnitsPerTick() * dt, Def.minParameter, Def.maxParameter);
	}
	@Nonnull
	public Transform GetLocalTransform0() {
		return Def.Apply(0f);
	}
	@Nonnull
	public Transform GetLocalTransform(float dt) {
		float dParam = GetParameter(dt);
		return Def.Apply(dParam);
	}

	public void Load(@Nonnull VehicleEntity vehicle, @Nonnull CompoundTag tags)
	{
		Parameter = tags.getFloat("param");
		Velocity = tags.getFloat("velocity");
	}

	@Nonnull
	public CompoundTag Save(@Nonnull VehicleEntity vehicle)
	{
		CompoundTag tags = new CompoundTag();
		tags.putFloat("param", Parameter);
		tags.putFloat("velocity", Velocity);
		return tags;
	}
}
