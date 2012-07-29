/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt.detail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 
 * @author Thor
 */
public class PromptBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		PromptService.schedulePromptService(context);
	}
}
