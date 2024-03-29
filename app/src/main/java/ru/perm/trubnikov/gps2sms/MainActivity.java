package ru.perm.trubnikov.gps2sms;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends BaseActivity {

    // Menu
    public static final int IDM_SETTINGS = 101;
    public static final int IDM_RATE = 105;
    public static final int IDM_DONATE = 106;

    // Activities
    private static final int ACT_RESULT_CHOOSE_CONTACT = 1001;
    private static final int ACT_RESULT_SETTINGS = 1002;
    private static final int ACT_RESULT_FAV = 1003;
    private static final int ACT_RESULT_REPO = 1004;

    // Dialogs
    private static final int SEND_SMS_DIALOG_ID = 0;
    private final static int SAVE_POINT_DIALOG_ID = 1;

    ProgressDialog mSMSProgressDialog;

    // My GPS states
    public static final int GPS_PROVIDER_DISABLED = 1;
    public static final int GPS_GETTING_COORDINATES = 2;
    public static final int GPS_GOT_COORDINATES = 3;
    //public static final int GPS_PROVIDER_UNAVIALABLE = 4;
    //public static final int GPS_PROVIDER_OUT_OF_SERVICE = 5;
    public static final int GPS_PAUSE_SCANNING = 6;

    // Location manager
    private LocationManager manager;

    // SMS thread
    ThreadSendSMS mThreadSendSMS;

    // Views
    ImageButton chooseContactBtn;
    ImageButton sendpbtn;
    ImageButton btnShare;
    ImageButton btnMap;
    ImageButton btnCopy;
    ImageButton btnSave;
    ImageButton btnFav;
    EditText plainPh;
    Button enableGPSBtn;
    TextView GPSstate;
    Menu mMenu;

    // Globals
    private String coordsToSend;
    private String coordsToShare;
    private String phoneToSendSMS;
    ColorStateList GPSSstateColorDefault;

    // Database
    DBHelper dbHelper;

    // Define the Handler that receives messages from the thread and update the
    // progress
    // SMS send thread. Result handling
    final Handler handler = new Handler() {
        public void handleMessage(Message msg) {

            String res_send = msg.getData().getString("res_send");
            // String res_deliver = msg.getData().getString("res_deliver");

            dismissDialog(SEND_SMS_DIALOG_ID);

            if (res_send.equalsIgnoreCase(getString(R.string.info_sms_sent))) {
                // HideKeyboard();
                Intent intent = new Intent(MainActivity.this,
                        AnotherMsgActivity.class);
                startActivity(intent);
            } else {
                DBHelper.ShowToastT(MainActivity.this, res_send,
                        Toast.LENGTH_SHORT);
            }

        }
    };

    // Location events (we use GPS only)
    private LocationListener locListener = new LocationListener() {

        public void onLocationChanged(Location argLocation) {
            printLocation(argLocation, GPS_GOT_COORDINATES);
        }

        @Override
        public void onProviderDisabled(String arg0) {
            printLocation(null, GPS_PROVIDER_DISABLED);
        }

        @Override
        public void onProviderEnabled(String arg0) {
        }

        @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }


    };

    private void printLocation(Location loc, int state) {

        String accuracy;

        switch (state) {
            case GPS_PROVIDER_DISABLED:
                GPSstate.setText(R.string.gps_state_disabled);
                setGPSStateAccentColor();
                enableGPSBtn.setVisibility(View.VISIBLE);
                break;
            case GPS_GETTING_COORDINATES:
                GPSstate.setText(R.string.gps_state_in_progress);
                setGPSStateNormalColor();
                enableGPSBtn.setVisibility(View.INVISIBLE);
                break;
            case GPS_PAUSE_SCANNING:
                GPSstate.setText("");
                enableGPSBtn.setVisibility(View.INVISIBLE);
                break;
            case GPS_GOT_COORDINATES:
                if (loc != null) {

                    // Accuracy
                    if (loc.getAccuracy() < 0.0001) {
                        accuracy = "?";
                    } else if (loc.getAccuracy() > 99) {
                        accuracy = "> 99";
                    } else {
                        accuracy = String.format(Locale.US, "%2.0f",
                                loc.getAccuracy());
                    }

                    String separ = System.getProperty("line.separator");

                    String la = String
                            .format(Locale.US, "%2.7f", loc.getLatitude());
                    String lo = String.format(Locale.US, "%3.7f",
                            loc.getLongitude());

                    coordsToSend = la + "," + lo;

                    coordsToShare = DBHelper.getShareBody(MainActivity.this,
                            coordsToSend, accuracy);

                    GPSstate.setText(getString(R.string.info_print1) + ": "
                            + accuracy + " " + getString(R.string.info_print2)
                            + separ + getString(R.string.info_latitude) + ": " + la
                            + separ + getString(R.string.info_longitude) + ": " + lo);

                    setGPSStateNormalColor();

                    btnShare.setVisibility(View.VISIBLE);
                    btnCopy.setVisibility(View.VISIBLE);
                    btnMap.setVisibility(View.VISIBLE);
                    btnSave.setVisibility(View.VISIBLE);
                    btnFav.setVisibility(View.VISIBLE);

                    ShowSendButton();
                    enableGPSBtn.setVisibility(View.INVISIBLE);

                } else {
                    GPSstate.setText(R.string.gps_state_unavialable);
                    setGPSStateAccentColor();
                    enableGPSBtn.setVisibility(View.VISIBLE);
                }
                break;
        }

    }

    // Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        mMenu = menu;

        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);

        menu.add(Menu.NONE, IDM_SETTINGS, Menu.NONE,
                R.string.menu_item_settings);
        menu.add(Menu.NONE, IDM_RATE, Menu.NONE, R.string.menu_item_rate);
        menu.add(Menu.NONE, IDM_DONATE, Menu.NONE, R.string.menu_item_donate);

        return (super.onCreateOptionsMenu(menu));
    }

    // Dialogs
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {

            case SEND_SMS_DIALOG_ID:
                mSMSProgressDialog = new ProgressDialog(MainActivity.this);
                // mCatProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mSMSProgressDialog.setCanceledOnTouchOutside(false);
                mSMSProgressDialog.setCancelable(false);
                mSMSProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mSMSProgressDialog.setMessage(getString(R.string.info_please_wait)
                        + " " + phoneToSendSMS);
                return mSMSProgressDialog;

            case SAVE_POINT_DIALOG_ID:
                LayoutInflater inflater_sp = getLayoutInflater();
                View layout_sp = inflater_sp.inflate(R.layout.repo_save_point_dialog,
                        (ViewGroup) findViewById(R.id.repo_save_point_dialog_layout));

                AlertDialog.Builder builder_sp = new AlertDialog.Builder(this);
                builder_sp.setView(layout_sp);

                final EditText lPointName = (EditText) layout_sp
                        .findViewById(R.id.point_edit_text);

                builder_sp.setPositiveButton(getString(R.string.save_btn_txt),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dbHelper = new DBHelper(MainActivity.this);
                                dbHelper.insertMyCoord(lPointName.getText()
                                        .toString(), coordsToSend);
                                dbHelper.close();
                                lPointName.setText(""); // Чистим
                                DBHelper.ShowToast(MainActivity.this,
                                        R.string.point_saved, Toast.LENGTH_LONG);
                            }
                        });

                builder_sp.setNegativeButton(getString(R.string.cancel_btn_txt),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                lPointName.setText(""); // Чистим
                                dialog.cancel();
                            }
                        });

                builder_sp.setCancelable(true);
                AlertDialog dialog = builder_sp.create();
                dialog.setTitle(getString(R.string.save_point_dlg_header));
                // show keyboard automatically
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                return dialog;

        }
        return null;
    }

    // Menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case IDM_SETTINGS:
                Intent sett_intent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                    sett_intent = new Intent(this, PreferencesActivity.class);
                } else {
                    sett_intent = new Intent(this, PreferencesLegacyActivity.class);
                }
                startActivityForResult(sett_intent, ACT_RESULT_SETTINGS);
                break;
            case R.id.action_sms_regexp:
                Intent repo_intent = new Intent(this, SlideTabsActivity.class);
                startActivityForResult(repo_intent, ACT_RESULT_REPO);
                break;
            case IDM_DONATE:
                Intent donate_intent = new Intent(this, DonateActivity.class);
                startActivity(donate_intent);
                break;
            case IDM_RATE:
                Intent int_rate = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id="
                                + getApplicationContext().getPackageName()));
                int_rate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    getApplicationContext().startActivity(int_rate);
                } catch (Exception e) {
                }
                break;

            default:
                return false;
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Держать ли экран включенным?
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (sharedPrefs.getBoolean("prefKeepScreen", true)) {
            getWindow()
                    .addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        // Возобновляем работу с GPS-приемником
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                locListener);

        btnShare.setVisibility(View.INVISIBLE);
        btnCopy.setVisibility(View.INVISIBLE);
        btnMap.setVisibility(View.INVISIBLE);
        btnSave.setVisibility(View.INVISIBLE);
        btnFav.setVisibility(View.INVISIBLE);

        HideSendButton();

        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            printLocation(null, GPS_GETTING_COORDINATES);
        }


    }

    @Override
    protected void onPause() {
        super.onPause();
        manager.removeUpdates(locListener);
    }

    public void showSelectedNumber(String number) {
        plainPh.setText(number);
        plainPh.setSelection(plainPh.getText().length());
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);

        switch (reqCode) {
            case (ACT_RESULT_CHOOSE_CONTACT):
                String number;
                String name;
                // int type = 0;
                if (data != null) {
                    Uri uri = data.getData();

                    if (uri != null) {
                        Cursor c = null;
                        try {

                            c = getContentResolver()
                                    .query(uri,
                                            new String[]{
                                                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                                                    ContactsContract.CommonDataKinds.Phone.TYPE,
                                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                                            null, null, null);

                            if (c != null && c.moveToFirst()) {
                                number = c.getString(0);
                                number = number.replace("-", "").replace(" ", "")
                                        .replace("(", "").replace(")", "").replace(".", "");
                                // type = c.getInt(1);
                                name = c.getString(2);
                                showSelectedNumber(number);

                                // update saved number
                                dbHelper = new DBHelper(MainActivity.this);
                                dbHelper.setSlot(0, name, number);
                                dbHelper.close();

                            }
                        } finally {
                            if (c != null) {
                                c.close();
                            }
                        }
                    }
                }

                break;
            case ACT_RESULT_SETTINGS:
                DBHelper.updateFavIcon(MainActivity.this, btnFav);
                restartApp();
                break;
            case ACT_RESULT_FAV:
                DBHelper.updateFavIcon(MainActivity.this, btnFav);
                break;
            case ACT_RESULT_REPO:
                DBHelper.updateFavIcon(MainActivity.this, btnFav);
                break;
        }
    }

    public void chooseContact(View v) {
        Intent intent = new Intent(Intent.ACTION_PICK,
                ContactsContract.Contacts.CONTENT_URI);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        startActivityForResult(intent, ACT_RESULT_CHOOSE_CONTACT);
        //IncomingSms.sendNotification(MainActivity.this, "56.5555555,56.7777777");
    }

    public void showGpsSystemDialog(View v) {
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            startActivity(new Intent(
                    android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
    }

    public void shareCoordinates(View v) {
        Intent sharingIntent = new Intent(
                android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        String shareBody = coordsToShare;
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                getString(R.string.share_topic));
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                shareBody);
        startActivity(Intent.createChooser(sharingIntent,
                getString(R.string.share_via)));
    }

    public void copyToClipboard(View v) {
        DBHelper.clipboardCopy(getApplicationContext(), coordsToSend);
        DBHelper.ShowToast(MainActivity.this, R.string.text_copied, Toast.LENGTH_LONG);
    }

    public void openOnMap(View v) {
        DBHelper.openOnMap(getApplicationContext(), coordsToSend);
    }

    public void saveCoordinates(View v) {
        showDialog(SAVE_POINT_DIALOG_ID);
    }

    public void chooseFavApp(View v) {
        if (!DBHelper.shareFav(MainActivity.this, coordsToShare)) {
            Intent intent = new Intent(MainActivity.this, ChooseFavActivity.class);
            startActivityForResult(intent, ACT_RESULT_FAV);
        }
    }

    // ------------------------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        // Setting up app language. This code MUST BE placed BEFORE setContentView!
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        String languageToLoad = sharedPrefs.getString("prefLang", "");
        if (!languageToLoad.equalsIgnoreCase("")) {
            Locale locale = new Locale(languageToLoad);
            Locale.setDefault(locale);
            Configuration config = new Configuration();
            config.locale = locale;
            getBaseContext().getResources().updateConfiguration(config,
                    getBaseContext().getResources().getDisplayMetrics());
        }
        // EOF Setting up app language

        setContentView(R.layout.activity_main);

        // Plain phone number
        plainPh = (EditText) findViewById(R.id.editText1);
        plainPh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String lPhone = ((EditText) v).getText().toString().replace("-", "")
                        .replace(" ", "").replace("(", "").replace(")", "").replace(".", "");

                if (lPhone.length() >= 5) {

                    Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(lPhone));
                    String[] projection = new String[]{"display_name"};
                    Cursor cursor = getContentResolver().query(uri, projection, null, null, null);

                    if (cursor.moveToFirst()) {
                        DBHelper.ShowToastT(MainActivity.this, cursor.getString(0),
                                Toast.LENGTH_SHORT);
                    }
                    cursor.close();
                }
            }
        });

        // Select contact
        chooseContactBtn = (ImageButton) findViewById(R.id.choose_contact);

        // Stored phone number -> to EditText
        dbHelper = new DBHelper(this);
        showSelectedNumber(dbHelper.getSlot(0, "phone"));
        dbHelper.close();

        // GPS init
        manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Enable GPS button
        enableGPSBtn = (Button) findViewById(R.id.button3);
        enableGPSBtn.setVisibility(View.INVISIBLE);

        // Share buttons
        btnShare = (ImageButton) findViewById(R.id.btnShare);
        btnCopy = (ImageButton) findViewById(R.id.btnCopy);
        btnMap = (ImageButton) findViewById(R.id.btnMap);
        btnSave = (ImageButton) findViewById(R.id.btnSave);
        btnFav = (ImageButton) findViewById(R.id.btnFav);
        btnShare.setVisibility(View.INVISIBLE);
        btnCopy.setVisibility(View.INVISIBLE);
        btnMap.setVisibility(View.INVISIBLE);
        btnSave.setVisibility(View.INVISIBLE);
        btnFav.setVisibility(View.INVISIBLE);

        btnFav.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                Intent intent = new Intent(MainActivity.this, ChooseFavActivity.class);
                startActivityForResult(intent, ACT_RESULT_FAV);
                return true;
            }
        });

        // Send buttons
        sendpbtn = (ImageButton) findViewById(R.id.send_plain);
        HideSendButton();

        // GPS-state TextView init
        GPSstate = (TextView) findViewById(R.id.textView1);
        GPSSstateColorDefault = GPSstate.getTextColors();
        setGPSStateNormalColor();
        DBHelper.updateFavIcon(MainActivity.this, btnFav);

    }

    private void setGPSStateNormalColor() {
        GPSstate.setTextColor(GPSSstateColorDefault);
    }

    private void setGPSStateAccentColor() {
        GPSstate.setTextColor(DBHelper.determineAccendcolor(MainActivity.this));
    }

    // ------------------------------------------------------------------------------------------

    protected void sendSMS(String lMsg) {

        phoneToSendSMS = plainPh.getText().toString().replace("-", "")
                .replace(" ", "").replace("(", "").replace(")", "").replace(".", "");

        if (phoneToSendSMS.equalsIgnoreCase("")) {
            DBHelper.ShowToast(MainActivity.this,
                    R.string.error_no_phone_number, Toast.LENGTH_LONG);
        } else {

            // update saved number
            dbHelper = new DBHelper(MainActivity.this);
            dbHelper.setSlot(0, "", phoneToSendSMS);
            dbHelper.close();

            showDialog(SEND_SMS_DIALOG_ID);

            // Запускаем новый поток для отправки SMS
            mThreadSendSMS = new ThreadSendSMS(handler, getApplicationContext());
            mThreadSendSMS.setMsg(lMsg);
            mThreadSendSMS.setPhone(phoneToSendSMS);
            mThreadSendSMS.setState(ThreadSendSMS.STATE_RUNNING);
            mThreadSendSMS.start();
        }

    }

    public void initiateSMSSend(View v) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        sendSMS(DBHelper.getLinkByProvType(sharedPrefs.getString("prefSMSContent", "2"), coordsToSend));
    }

    protected void ShowSendButton() {
        sendpbtn.setVisibility(View.VISIBLE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) plainPh.getLayoutParams();
        params.addRule(RelativeLayout.LEFT_OF, R.id.send_plain);
        plainPh.setLayoutParams(params); //causes layout update
    }

    protected void HideSendButton() {
        sendpbtn.setVisibility(View.INVISIBLE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) plainPh.getLayoutParams();
        params.addRule(RelativeLayout.LEFT_OF, 0);
        plainPh.setLayoutParams(params); //causes layout update
    }

    private void restartApp() {
        Intent i = getBaseContext().getPackageManager()
                .getLaunchIntentForPackage(getBaseContext().getPackageName());
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }


	/*
     * TODO
	 *
	 * Добавить в маркет версию для Android 2.1 (API 7) отдельным приложением
	 * Перехват СМС работает только если экран включен (сделать пуш-ап уведомления)
	 *
	 * Удаление СМС, Фотку на кнопке с выбранным контактом
	 * Refactor MyCoordsActivity and MySMSActivity (Create common parent class?)
	 */

}
