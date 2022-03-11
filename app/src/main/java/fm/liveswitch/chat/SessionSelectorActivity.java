package fm.liveswitch.chat;

import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.*;
import android.content.*;
import android.text.*;
import android.view.*;
import android.widget.*;

public class SessionSelectorActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static String[] Names = {
            "Aurora",
            "Argus",
            "Baker",
            "Blackrock",
            "Caledonia",
            "Coquitlam",
            "Doom",
            "Dieppe",
            "Eon",
            "Elkhorn",
            "Fairweather",
            "Finlayson",
            "Garibaldi",
            "Grouse",
            "Hoodoo",
            "Helmer",
            "Isabelle",
            "Itcha",
            "Jackpass",
            "Josephine",
            "Klinkit",
            "King Albert",
            "Lilliput",
            "Lyall",
            "Mallard",
            "Douglas",
            "Nahlin",
            "Normandy",
            "Omega",
            "One Eye",
            "Pukeashun",
            "Plinth",
            "Quadra",
            "Quartz",
            "Razerback",
            "Raleigh",
            "Sky Pilot",
            "Swannell",
            "Tatlow",
            "Thomlinson",
            "Unnecessary",
            "Upright",
            "Vista",
            "Vedder",
            "Whistler",
            "Washington",
            "Yeda",
            "Yellowhead",
            "Zoa"
    };

    private Button joinButton;
    private EditText channelText;
    private EditText nameText;
    private Spinner modeSpinner;
    private CheckBox audioOnlyCheckBox;
    private CheckBox receiveOnlyCheckBox;
    private CheckBox simulcastCheckBox;
    private CheckBox screenShareCheckBox;
    private App app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        setContentView(R.layout.activity_session_selector);

        app = App.getInstance(this);

        nameText = (EditText)findViewById(R.id.userNameInput);
        channelText = (EditText)findViewById(R.id.channelInput);
        joinButton = (Button)findViewById(R.id.joinButton);

        audioOnlyCheckBox = (CheckBox)findViewById(R.id.audioOnlyInput);
        receiveOnlyCheckBox = (CheckBox)findViewById(R.id.receiveOnlyInput);
        simulcastCheckBox = (CheckBox) findViewById(R.id.simulcastInput);
        screenShareCheckBox = (CheckBox)findViewById(R.id.screenShareInput);

        // Populate our mode spinner with App.Mode values and default app.mode to the first.
        modeSpinner = (Spinner) findViewById(R.id.connectionModeInput);
        modeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, App.Modes.values()));
        modeSpinner.setOnItemSelectedListener(this);
        app.setMode(App.Modes.values()[0]);

        try
        {
            // Create a random 6 digit number for the new channel ID.
            channelText.setText(String.valueOf(new fm.liveswitch.Randomizer().next(100000, 999999)));
            channelText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(6) });

            nameText.setText(Names[new fm.liveswitch.Randomizer().next(Names.length)]);
            nameText.setFilters(new InputFilter[] { new InputFilter.LengthFilter(20) });

            joinButton.setOnClickListener(new View.OnClickListener()
            {
                public void onClick(View v)
                {
                    switchToVideoChat(channelText.getText().toString(), nameText.getText().toString());
                }
            });

            audioOnlyCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setAudioOnly(b);
                    if (b) {
                        simulcastCheckBox.setChecked(false);
                    }
                    simulcastCheckBox.setEnabled(!b);
                }
            });

            receiveOnlyCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setReceiveOnly(b);
                    if (b) {
                        simulcastCheckBox.setChecked(false);
                    }
                    simulcastCheckBox.setEnabled(!b);
                }
            });

            screenShareCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setEnableScreenShare(b);
                }
            });

            simulcastCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    app.setEnableSimulcast(b);
                    if (b) {
                        audioOnlyCheckBox.setChecked(false);
                        receiveOnlyCheckBox.setChecked(false);
                    }
                    audioOnlyCheckBox.setEnabled(!b);
                    receiveOnlyCheckBox.setEnabled(!b);
                }
            });

            ((TextView)findViewById(R.id.phoneText)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent tel = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:18883796686"));
                    startActivity(tel);
                }
            });

            ((TextView)findViewById(R.id.emailText)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri data = Uri.parse("mailto:info@frozenmountain.com");
                    intent.setData(data);
                    startActivity(intent);
                }
            });

            ((ImageView)findViewById(R.id.facebookIcon)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri data = Uri.parse("https://www.facebook.com/frozenmountain");
                    intent.setData(data);
                    startActivity(intent);
                }
            });

            ((ImageView)findViewById(R.id.twitterIcon)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri data = Uri.parse("https://twitter.com/frozenmountain");
                    intent.setData(data);
                    startActivity(intent);
                }
            });

            ((ImageView)findViewById(R.id.linkedinIcon)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri data = Uri.parse("https://www.linkedin.com/company/frozen-mountain-software");
                    intent.setData(data);
                    startActivity(intent);
                }
            });
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    // Handle mode selection
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        app.setMode((App.Modes)parent.getItemAtPosition(pos));
        boolean modeIsPeer = app.getMode() == App.Modes.Peer;
        if (modeIsPeer) {
            simulcastCheckBox.setChecked(false);
        }
        simulcastCheckBox.setEnabled(!modeIsPeer);
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // We don't care
    }

    protected void onActivityResult (int requestCode, int resultCode, Intent data) {
        if (requestCode == 42 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (data == null) {
                alert("Must allow screen sharing before the chat can start.");
            } else {
                MediaProjectionManager manager = (MediaProjectionManager) this.getSystemService(MEDIA_PROJECTION_SERVICE);
                app.setMediaProjection(manager.getMediaProjection(resultCode, data));
                startActivity(new Intent(getApplicationContext(), ChatActivity.class));
            }
        }
    }

    private void switchToVideoChat(String channelId, String name)
    {
        if (channelId.length() == 6)
        {
            if (name.length() > 0)
            {
                app.setChannelId(channelId);
                app.setUserName(name);
                app.setEnableScreenShare(screenShareCheckBox.isChecked());

                if (screenShareCheckBox.isChecked() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    MediaProjectionManager manager = (MediaProjectionManager)this.getSystemService(MEDIA_PROJECTION_SERVICE);
                    Intent screenCaptureIntent = manager.createScreenCaptureIntent();

                    this.startActivityForResult(screenCaptureIntent, 42);
                } else {
                    // Show the video chat.
                    startActivity(new Intent(getApplicationContext(), ChatActivity.class));
                }
            }
            else
            {
                alert("Must have a name.");
            }
        }
        else
        {
            alert("Channel ID must be 6 digits long.");
        }
    }

    public void alert(String format, Object... args)
    {
        final String text = String.format(format, args);
        final Activity self = this;
        self.runOnUiThread(new Runnable()
        {
            public void run()
            {
                if (!isFinishing())
                {
                    AlertDialog.Builder alert = new AlertDialog.Builder(self);
                    alert.setMessage(text);
                    alert.setPositiveButton("OK", new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int which)
                        { }
                    });
                    alert.show();
                }
            }
        });
    }
}
