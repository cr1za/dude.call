package com.cr1za.dudecall;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/** The entire app: save a number once, and call it from one button. */
public final class MainActivity extends Activity {
    private static final String PREFS_NAME = "dude_call_storage";
    private static final String KEY_PHONE = "phone_number";
    private static final int CALL_PERMISSION_REQUEST = 123;

    private String phoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.BLACK);

        setContentView(new CallButtonView(this, this::onCallButtonPressed));

        SharedPreferences storage = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        phoneNumber = storage.getString(KEY_PHONE, null);
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            showFirstLaunchSetup(storage);
        }
    }

    private void showFirstLaunchSetup(SharedPreferences storage) {
        EditText numberInput = new EditText(this);
        numberInput.setSingleLine(true);
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        numberInput.setHint("+41 79 123 45 67");
        numberInput.setContentDescription("Phone number to call");

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24f * getResources().getDisplayMetrics().density);
        container.setPadding(padding, 0, padding, 0);
        container.addView(numberInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Set call number")
                .setMessage("Enter the number once. It cannot be changed until you uninstall and reinstall the app.")
                .setView(container)
                .setPositiveButton("SAVE", null)
                .setCancelable(false)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String normalized = normalizePhoneNumber(numberInput.getText().toString());
                    if (!normalized.matches("\\+?[0-9]{2,20}")) {
                        numberInput.setError("Enter a valid phone number");
                        return;
                    }
                    if (!storage.edit().putString(KEY_PHONE, normalized).commit()) {
                        Toast.makeText(this, "Could not save number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    phoneNumber = normalized;
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private static String normalizePhoneNumber(String input) {
        // Spaces and common separators are only cosmetic; reject other characters.
        return input.trim().replaceAll("[\\s().-]", "");
    }

    private void onCallButtonPressed() {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION_REQUEST);
        } else {
            placeCall();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CALL_PERMISSION_REQUEST
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            placeCall();
        }
    }

    private void placeCall() {
        if (phoneNumber == null) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", phoneNumber, null));
            startActivity(intent);
        } catch (SecurityException | ActivityNotFoundException error) {
            Toast.makeText(this, "This device cannot place the call", Toast.LENGTH_SHORT).show();
        }
    }
}
