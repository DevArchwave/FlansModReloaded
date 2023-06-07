package com.flansmod.common.actions;

import com.flansmod.common.types.elements.ActionDefinition;
import com.flansmod.common.types.guns.GunContext;
import com.flansmod.util.Maths;
import net.minecraft.world.InteractionHand;

public abstract class Action
{
	public static final float TICK_RATE = 1.0f / 20.0f;
	public ActionDefinition actionDef;
	protected int progress = 0;
	protected int duration = 0;
	public int GetProgressTicks() { return progress; }
	public int GetDurationTicks() { return duration; }
	public float GetProgressSeconds() { return progress * TICK_RATE; }
	public float GetDurationSeconds() { return duration * TICK_RATE; }

	public InteractionHand hand = InteractionHand.MAIN_HAND;

	public boolean Finished() { return progress >= duration; }

	public Action(ActionDefinition def, InteractionHand hand)
	{
		this.actionDef = def;
		this.duration = Maths.Ceil(def.duration);
		this.progress = 0;
		this.hand = hand;
	}

	public boolean CanStart(GunContext context) { return true; }

	public void OnStartServer(GunContext context) { progress = 0; }
	public void OnTickServer(GunContext context) { progress++; }
	public void OnFinishServer(GunContext context) {}

	public void OnStartClient(GunContext context) { progress = 0; }
	public void OnTickClient(GunContext context) { progress++; }
	public void OnFinishClient(GunContext context) {}

}
