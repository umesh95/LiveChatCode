package layout;

import android.support.v4.app.Fragment;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import fm.liveswitch.chat.App;
import fm.liveswitch.chat.R;

public class TextChatFragment extends Fragment implements App.OnReceivedTextListener {
    private App app;
    private OnTextReadyListener listener;
    private TextView textView;
    private Button submitButton;
    private EditText editText;
    private final String CHAT_TEXT_KEY = "CHAT_TEXT_KEY";

    public TextChatFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        app = App.getInstance(null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (textView != null) {
            outState.putString(CHAT_TEXT_KEY, textView.getText().toString());
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_text_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textView = (TextView)view.findViewById(R.id.log);
        editText = (EditText)view.findViewById(R.id.text);
        submitButton = (Button)view.findViewById(R.id.send);

        //Disable Chat UI until registration is complete.
        enableChatUI(false);

        textView.setMovementMethod(new ScrollingMovementMethod());
        if (savedInstanceState != null && savedInstanceState.getString(CHAT_TEXT_KEY) != null) {
            textView.setText(savedInstanceState.getString(CHAT_TEXT_KEY));
        }

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String msg = editText.getText().toString().trim();
                editText.setText("");

                if (msg.length() > 0) {
                    app.writeLine(msg);
                }
            }
        });

        listener.onTextReady();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public void onReceivedText(final String name, String message)
    {
        final String concatMessage = name + ": " + message + "\n";
        writeLine(new SpannableString(concatMessage)
        {{
            setSpan(new StyleSpan(Typeface.BOLD), 0, name.length(), 0);
        }});
    }

    public void onClientRegistered()
    {
        enableChatUI(true);
    }

    public void onClientUnregistered()
    {
        enableChatUI(false);
    }

    private void enableChatUI(boolean enable)
    {
        final boolean localEnable = enable;
        if(getActivity() != null)
        {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    editText.setEnabled(localEnable);
                    submitButton.setEnabled(localEnable);
                }
            });
        }
    }

    public void onPeerJoined(String name)
    {
        final String message = name + " has joined.\n";
        writeLine(new SpannableString(message)
        {{
            setSpan(new StyleSpan(Typeface.BOLD), 0, message.length(), 0);
        }});
    }

    public void onPeerLeft(String name)
    {
        final String message = name + " has left.\n";
        writeLine(new SpannableString(message)
        {{
            setSpan(new StyleSpan(Typeface.BOLD), 0, message.length(), 0);
        }});
    }

    private void writeLine(final SpannableString str)
    {
        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView.setText(TextUtils.concat(str, textView.getText()));
                    listener.onNewMessage();
                }
            });
        }
    }

    public interface OnTextReadyListener {
        void onTextReady();
        void onNewMessage();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnTextReadyListener) {
            listener = (OnTextReadyListener) context;
        } else {
            throw new ClassCastException(context.toString()
                    + " must implement TextChatFragment.OnTextReadyListener");
        }
    }
}
