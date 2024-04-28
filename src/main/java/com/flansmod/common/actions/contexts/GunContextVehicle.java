package com.flansmod.common.actions.contexts;

import com.flansmod.common.actions.ActionStack;
import com.flansmod.common.entity.vehicle.guns.VehicleGunSaveState;

import javax.annotation.Nonnull;

public class GunContextVehicle extends GunContextInventoryItem
{
	protected final ShooterContextVehicle VehicleContext;
	protected final int GunIndex;

	@Nonnull
	protected VehicleGunSaveState State() { return VehicleContext.Vehicle.Guns().GetGunStateAtIndex(GunIndex); }

	public GunContextVehicle(@Nonnull ShooterContextVehicle vehicle, int gunIndex)
	{
		super(vehicle.GetAttachedInventory(), vehicle.GunSlotIndex(gunIndex));
		VehicleContext = vehicle;
		GunIndex = gunIndex;
	}

	@Override
	@Nonnull
	public ActionStack GetActionStack()
	{
		ActionStack current = State().Actions;
		return current != null ? current : ActionStack.Invalid;
	}
}
