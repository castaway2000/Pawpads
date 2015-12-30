package saberapplications.pawpads.ui.chat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.quickblox.chat.QBChat;
import com.quickblox.chat.QBChatService;
import com.quickblox.chat.QBPrivateChat;
import com.quickblox.chat.exception.QBChatException;
import com.quickblox.chat.listeners.QBMessageListener;
import com.quickblox.chat.listeners.QBPrivateChatManagerListener;
import com.quickblox.chat.model.QBChatMessage;
import com.quickblox.chat.model.QBDialog;
import com.quickblox.core.QBEntityCallbackImpl;
import com.quickblox.core.request.QBRequestGetBuilder;

import java.util.ArrayList;
import java.util.List;

import saberapplications.pawpads.ChatObject;
import saberapplications.pawpads.R;
import saberapplications.pawpads.Util;


public class ChatActivity extends Activity {

    public static final String EXTRA_DIALOG ="dialog" ;
    //EditText editText_mail_id;
    EditText editText_chat_message;
    ListView listView_chat_messages;
    Button button_send_chat;
    private List<ChatObject> chat_list;
    BroadcastReceiver recieve_chat;
    private QBDialog dialog;
    private ChatAdapter chatAdapter;

    private QBMessageListener messageListener =new QBMessageListener() {
        @Override
        public void processMessage(QBChat qbChat, final QBChatMessage qbChatMessage) {
            ChatActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    chatAdapter.add(new ChatObject(qbChatMessage.getBody(), ChatObject.RECEIVED));
                }
            });

        }

        @Override
        public void processError(QBChat qbChat, QBChatException e, QBChatMessage qbChatMessage) {

        }

    };

    QBPrivateChatManagerListener privateChatManagerListener = new QBPrivateChatManagerListener() {
        @Override
        public void chatCreated(final QBPrivateChat privateChat, final boolean createdLocally) {
            if(!createdLocally){
                privateChat.addMessageListener(messageListener);
            }
        }
    };

    private QBPrivateChat chat;
    private Integer currentUserId;
    private int sendTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        setTitle("PawPads | Chat");


        //editText_mail_id = (EditText) findViewById(R.id.editText_mail_id);
        //editText_mail_id.setText(getIntent().getExtras().getString("user", null));
        final String recipient = getIntent().getExtras().getString("user", null);
        final String displayPic = getIntent().getExtras().getString("image", null);
        ((android.widget.TextView)findViewById(R.id.chat_header_recipient_name)).setText(recipient);
        ImageView iv = (ImageView) findViewById(R.id.chat_header_profile_image);
        ImageLoader il = ImageLoader.getInstance();
        il.displayImage(displayPic, iv);
        editText_chat_message = (EditText) findViewById(R.id.editText_chat_message);
        listView_chat_messages = (ListView) findViewById(R.id.listView_chat_messages);
        button_send_chat = (Button) findViewById(R.id.button_send_chat);
        button_send_chat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // send chat message to server

                QBChatMessage msg = new QBChatMessage();
                msg.setBody(editText_chat_message.getText().toString());
                msg.setProperty("save_to_history", "1");
                msg.setRecipientId(sendTo);
                msg.setDialogId(dialog.getDialogId());
                msg.setProperty("send_to_chat", "1");
                QBChatService.createMessage(msg,new QBEntityCallbackImpl<QBChatMessage>(){
                    @Override
                    public void onSuccess(QBChatMessage result, Bundle params) {
                        super.onSuccess(result, params);
                        showChat(ChatObject.SENT,result.getBody());
                    }
                });

                editText_chat_message.setText("");
            }
        });

        if (getIntent()!=null){
            dialog = (QBDialog) getIntent().getSerializableExtra(EXTRA_DIALOG);
        }else if(savedInstanceState!=null){
            dialog= (QBDialog) savedInstanceState.get(EXTRA_DIALOG);
        }


        QBRequestGetBuilder requestBuilder = new QBRequestGetBuilder();
        requestBuilder.setPagesLimit(100);
        QBChatService.getDialogMessages(dialog, requestBuilder, new QBEntityCallbackImpl<ArrayList<QBChatMessage>>() {
            @Override
            public void onSuccess(ArrayList<QBChatMessage> result, Bundle params) {

                if (chat_list == null || chat_list.size() == 0) {
                    chat_list = new ArrayList<>();
                }
                for (QBChatMessage qbChatMessage : result) {
                    String type = currentUserId.equals( qbChatMessage.getRecipientId()) ? ChatObject.RECEIVED : ChatObject.SENT;
                    chat_list.add(new
                            ChatObject(qbChatMessage.getBody(), type));

                }
                chatAdapter = new ChatAdapter(ChatActivity.this, R.layout.chat_view, chat_list);
                listView_chat_messages.setAdapter(chatAdapter);

                //chatAdapter.notifyDataSetChanged();
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ChatActivity.this);
        currentUserId = prefs.getInt(Util.QB_USERID, 0);
        sendTo=0;
        for(Integer userid:dialog.getOccupants()){
            if (userid.intValue()!=currentUserId){
                sendTo=userid;
            }
        }
        QBChatService.getInstance().getPrivateChatManager().addPrivateChatManagerListener(privateChatManagerListener);
    }


    @Override
    protected void onStop() {
        super.onStop();

    }

    private void showChat(String type, String message) {
        chatAdapter.add(new ChatObject(message, type));
        chatAdapter.notifyDataSetChanged();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(EXTRA_DIALOG,dialog);
    }
}
