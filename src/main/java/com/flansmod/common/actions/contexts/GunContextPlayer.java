package com.flansmod.common.actions.contexts;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class GunContextPlayer extends GunContextLiving
{
	private final Player Player;
	private final int InventorySlot;

	public GunContextPlayer(ShooterContextPlayer shooter, int inventorySlot)
	{
		super(shooter, inventorySlot == ((Player)shooter.Shooter).getInventory().selected ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, inventorySlot);

		Player = (Player)shooter.Shooter;
		InventorySlot = inventorySlot;
	}

	@Override
	public void OnItemStackChanged(ItemStack stack)
	{
		ItemStack stackInSlot = Player.getInventory().getItem(InventorySlot);
		Player.getInventory().setItem(InventorySlot, stack);
	}
	@Override
	public boolean UpdateFromItemStack()
	{
		ItemStack currentStack = Player.getInventory().getItem(InventorySlot);
		Stack = currentStack.copy();
		return false;
	}
	@Override
	public Inventory GetAttachedInventory()
	{
		return Player.getInventory();
	}
	@Override
	public int GetInventorySlotIndex() { return InventorySlot; }
	@Override
	public String toString()
	{
		return "GunContextPlayer:" + GetItemStack().toString() + " held by " + Player + " in slot " + InventorySlot;
	}
}
