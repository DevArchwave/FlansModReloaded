package com.flansmod.common.types.crafting.elements;

import com.flansmod.common.types.JsonField;

public class ItemHoldingDefinition
{
	@JsonField
	public ItemHoldingSlotDefinition[] slots = new ItemHoldingSlotDefinition[0];
}
