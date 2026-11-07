package com.kdt.mcgui;

import android.content.*;
import android.graphics.*;
import android.util.*;

import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {
	
	public MineButton(Context ctx) {
		this(ctx, null);
	}
	
	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.noto_sans_bold));
		setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.orbit_play_bg, null));
		setTextColor(0xFF04110A);
		setLetterSpacing(0.08f);
		setStateListAnimator(null);
		if (android.os.Build.VERSION.SDK_INT >= 28) { setOutlineSpotShadowColor(0xFF3FCF6D); setOutlineAmbientShadowColor(0xFF3FCF6D); }
		setElevation(getResources().getDisplayMetrics().density * 10);
		// Orbit: a slow breathing pulse, like the PC launcher's PLAY
		android.animation.ObjectAnimator pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(this,
				android.animation.PropertyValuesHolder.ofFloat(SCALE_X, 1f, 1.025f), android.animation.PropertyValuesHolder.ofFloat(SCALE_Y, 1f, 1.025f));
		pulse.setDuration(1400); pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE); pulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
		pulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
		post(pulse::start);
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));
	}

}
