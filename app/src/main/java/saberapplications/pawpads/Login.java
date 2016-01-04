package saberapplications.pawpads;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.quickblox.auth.QBAuth;
import com.quickblox.auth.model.QBSession;
import com.quickblox.core.QBEntityCallbackImpl;
import com.quickblox.users.QBUsers;
import com.quickblox.users.model.QBUser;

import java.util.Calendar;
import java.util.List;

import saberapplications.pawpads.ui.home.MainActivity;

/**
 * Created by blaze on 10/21/2015.
 */
public class Login  extends AppCompatActivity implements View.OnClickListener{
    UserLocalStore userLocalStore;
    Button bLogin;
    EditText etUsername, etPassword;
    TextView registerLink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        setTitle("PawPads | Login");

        etUsername = (EditText) findViewById(R.id.etUsername);
        etPassword = (EditText) findViewById(R.id.etPassword);
        bLogin = (Button) findViewById(R.id.bLogin);
        registerLink = (TextView) findViewById(R.id.tvRegisterLink);
        userLocalStore = new UserLocalStore(this);

        bLogin.setOnClickListener(this);
        registerLink.setOnClickListener(this);
    }

    @Override
    public void onClick(View v){
        switch (v.getId()){
            case R.id.bLogin:
                final String username = etUsername.getText().toString();
                final String password = etPassword.getText().toString();


                QBAuth.createSession(new QBEntityCallbackImpl<QBSession>() {

                    @Override
                    public void onSuccess(final QBSession session, Bundle params) {

                        QBUser qbUser = new QBUser();
                        qbUser.setLogin(username);
                        qbUser.setPassword(password);

                        QBUsers.signIn(qbUser, new QBEntityCallbackImpl<QBUser>() {
                            @Override
                            public void onSuccess(QBUser user, Bundle params) {
                                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(Login.this).edit();
                                editor.putString(Util.QB_USER, username);
                                editor.putString(Util.USER_NAME, username);
                                editor.putString(Util.QB_PASSWORD, password);
                                editor.putInt(Util.QB_USERID, user.getId());
                                editor.apply();
                                Intent intent = new Intent(Login.this, profilepage.class);
                                startActivity(intent);

                                finish();
                            }
                            @Override
                            public void onError(List<String> errors) {
                                Util.onError(errors, Login.this);
                            }
                        });
                    }

                    @Override
                    public void onError(List<String> errors) {
                        Util.onError(errors,Login.this);
                    }
                });


                break;
            case R.id.tvRegisterLink:
                startActivity(new Intent(this, Register.class));
                break;
        }
    }



    private void showErrorMessage(){
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(Login.this);
        dialogBuilder.setMessage("Incorrect username or Password");
        dialogBuilder.setPositiveButton("Ok", null);
        dialogBuilder.show();
    }

    private void logUserIn(User returnedUser){
        userLocalStore.storeUserData(returnedUser);
        userLocalStore.setUserLoggedIn(true);
        String test = userLocalStore.getLoggedInUser().username;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
