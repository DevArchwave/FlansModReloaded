package com.flansmod.common.types.vehicles.elements;

import com.flansmod.common.types.JsonField;
import com.flansmod.common.types.vehicles.EDrivingControl;

public class ControlSchemeAxisDefinition
{
	public static final ControlSchemeAxisDefinition INVALID = new ControlSchemeAxisDefinition();

	@JsonField
	public EDrivingControl positive = EDrivingControl.Accelerate;
	@JsonField
	public EDrivingControl negative = EDrivingControl.Unset;

	@JsonField
	public EAxisBehaviourType axisBehaviour = EAxisBehaviourType.SliderWithRestPosition;
	@JsonField
	public float minValue = -1.0f;
	@JsonField
	public float maxValue = 1.0f;

	@JsonField
	public float startingPosition = 0.0f;

	@JsonField(Docs = "Used if you select SliderWithRestPosition")
	public float restingPosition = 0.0f;

	@JsonField(Docs = "Used if you select a Notched slider")
	public float[] notches = new float[0];
}
