/* Copyright 2010 NorseVision LLC, all rights reserved. */
package com.lucyapps.prompt;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.lucyapps.prompt.detail.LicensingService;
import com.lucyapps.prompt.detail.PromptService;

/**
 * 
 * @author Thor Carpenter (thorcarpenter@lucyapps.com)
 */
public class PromptActivity extends Activity {

	private static final int DIALOG_HELP = 0;
	private static final String IS_FIRST_RUN_ID = ".isFirstRun";
	private ImageButton startServiceButton;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle bundle) {
		try {
			super.onCreate(bundle);

			setContentView(R.layout.toggle_button);
			startServiceButton = (ImageButton) this.findViewById(R.id.toggle_button);
			startServiceButton.setOnClickListener(new OnClickListener() {

				public void onClick(View v) {
					// Toggle the state
					if (isPromptServiceScheduledToRun()) {
						stopPromptService();
					} else {
//						if (checkLicense()) {
							startPromptService();
//						}
					}
				}
			});
			
//	        checkLicense();
			
			if (isPromptServiceScheduledToRun()) {
				startPromptService();
			} else {
				stopPromptService();
			}
			
			if (isFirstRun()) {
				showDialog(DIALOG_HELP);
			}
		} catch (Throwable t) {
			Log.w("LucyApps", t);
		}
	}

	@Override
	protected void onDestroy() {
		try {
			super.onDestroy();
		} catch (Throwable t) {
			Log.w("LucyApps", t);
		}
	}
	
	private SharedPreferences getServiceSharedPreferences() {
		return PreferenceManager.getDefaultSharedPreferences(getBaseContext());
	}
		
	private boolean isFirstRun() {
		SharedPreferences prefs = getServiceSharedPreferences();
		final boolean isFirstRun = prefs.getBoolean(IS_FIRST_RUN_ID, true);
		if (isFirstRun) {
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean(IS_FIRST_RUN_ID, false);
			editor.commit();
		}
		return isFirstRun;
	}

	private boolean isPromptServiceScheduledToRun() {
		return PromptService.isScheduledToRunPref(getServiceSharedPreferences());
	}

	private void setPromptServiceScheduledToRun(boolean shouldRun) {
		SharedPreferences.Editor editor = getServiceSharedPreferences().edit();
		PromptService.setScheduledToRunPref(editor, shouldRun);
		editor.commit();
	}

	private void startPromptService() {
		try {
			// TODO if !isGPSEnabled let user know

			setPromptServiceScheduledToRun(true);
			PromptService.schedulePromptService(this);
			
			startServiceButton.setImageResource(R.drawable.ic_prompt_on);
			// startServiceButton.setText(R.string.title_stop);
			
			Toast.makeText(this, R.string.local_service_started, Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Log.e("LucyApps", "Error starting PromptService", e);
		}
	}

	private void stopPromptService() {
		try {
			setPromptServiceScheduledToRun(false);
			PromptService.cancelPromptService(this);
			
			startServiceButton.setImageResource(R.drawable.ic_prompt_off);
			// startServiceButton.setText(R.string.title_start);
			
			Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
			Log.e("LucyApps", "Error stopping PromptService", e);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.activity_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.help:
			showDialog(DIALOG_HELP);
			return true;
		case R.id.options_menu:
			Intent settingsActivity = new Intent(getBaseContext(), Preferences.class);
			startActivity(settingsActivity);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_HELP:
			return createHelpDialog();
		}
		return null;
	}

	private Dialog createHelpDialog() {
		final Context context = this;
		final TextView message = new TextView(context);
		final SpannableString s = new SpannableString(context.getText(R.string.help_msg));
		Linkify.addLinks(s, Linkify.EMAIL_ADDRESSES);
		message.setText(s);
		message.setMovementMethod(LinkMovementMethod.getInstance());
		
		return new AlertDialog.Builder(context)
			.setTitle(R.string.help)
			.setCancelable(true)
			.setIcon(android.R.drawable.ic_menu_help)
			.setPositiveButton(R.string.ok_str, null)
			.setView(message)
			.create();
	}
	
	// Add check to service too
	private boolean checkLicense() {
		int licenseState = LicensingService.getLicenceState(getServiceSharedPreferences());
		if (licenseState != 0 && licenseState != 1) {
			stopPromptService();
        	Toast.makeText(PromptActivity.this, R.string.invalid_license_str, Toast.LENGTH_LONG).show();
        	sendUserToMarket();
        	return false;
		}
		return true;
    }
	
	public static Intent getSendUserToMarketIntent(String packageName) {
		return new Intent(Intent.ACTION_VIEW, Uri.parse(
                "market://details?id=" + packageName));
	}
		
	public void sendUserToMarket() {
        startActivity(getSendUserToMarketIntent(getPackageName()));
	}
}
