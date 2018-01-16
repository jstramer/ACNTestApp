package at.tugraz.acn.acntestapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import java.io.DataOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ACN";

    private Spinner s_cipherSuites;
    private EditText et_server;
    private Button b_startHandshake;
    private Button b_sendIMEI;
    private Button b_sendNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        et_server = (EditText) findViewById(R.id.et_server);
        s_cipherSuites = (Spinner) findViewById(R.id.s_cipherSuites);
        b_startHandshake = (Button) findViewById(R.id.b_startHandshake);
        b_sendIMEI = (Button) findViewById(R.id.b_sendIMEI);
        b_sendNumber = (Button) findViewById(R.id.b_sendNumber);

        // get permission to extract imei and number (api level > 23)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_PHONE_STATE}, 123);
        }

        fillCipherSuites();

        b_startHandshake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "b_startHandshake clicked");
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... voids) {
                        try {
                            SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
                            SSLSocket s = (SSLSocket) ssf.createSocket(et_server.getText().toString(), 443);
                            s.setEnabledCipherSuites(new String[]{s_cipherSuites.getSelectedItem().toString()});
                            s.startHandshake();
                        } catch (SSLHandshakeException e) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(MainActivity.this);
                                    dlgAlert.setMessage("Handshake failed. Either cannot connect to the server or the server does not support the chosen cipher suite");
                                    dlgAlert.setTitle("Handshake Error");
                                    dlgAlert.setPositiveButton("OK", null);
                                    dlgAlert.setCancelable(true);
                                    dlgAlert.create().show();
                                }
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });

        b_sendIMEI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "b_sendIMEI clicked");
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... voids) {
                        HashMap<String, String> parameters = new HashMap<>();
                        parameters.put("IMEI", getIMEI());

                        sendHttpRequest(parameters);

                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });

        b_sendNumber.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "b_sendIMEI clicked");
                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... voids) {
                        HashMap<String, String> parameters = new HashMap<>();
                        parameters.put("Number", getPhoneNumber());

                        sendHttpRequest(parameters);

                        return null;
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
    }

    private void fillCipherSuites() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        try {
            SSLContext context = SSLContext.getDefault();
            SSLSocketFactory factory = (SSLSocketFactory)context.getSocketFactory();
            SSLSocket socket = (SSLSocket)factory.createSocket();
            String[] ciphers = socket.getSupportedCipherSuites();

            adapter.addAll(ciphers);
        } catch (Exception e) {
            e.printStackTrace();
        }
        s_cipherSuites.setAdapter(adapter);
    }

    private void sendHttpRequest(HashMap<String, String> parameters) {
        StringBuilder builder = new StringBuilder("");
        for (Map.Entry<String, String> entry : parameters.entrySet())
        {
            if (builder.length() > 0)
                builder.append("&");

            builder.append(entry.getKey());
            builder.append("=");
            builder.append(entry.getValue());
        }

        String params = builder.toString();

        // 0 = GET parameters
        // 1 = Header
        // 2 = POST parameters
        int random = ThreadLocalRandom.current().nextInt(0, 3);

        Log.i(TAG, "Sending HTTP Request - Using " + ((random == 0) ? "GET" : ((random == 1) ? "Headers" : "POST")));

        String urlString = "http://www.december.com/html/demo/hello.html" +
                ((random == 0) ? "?" + params : "");

        try {
            URL url = new URL(urlString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setUseCaches(false);
            urlConnection.setRequestMethod((random == 2) ? "POST" : "GET");

            if (random == 1) {
                for (Map.Entry<String, String> entry : parameters.entrySet())
                {
                    urlConnection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if (random == 2) {
                DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream());
                wr.write(params.getBytes(StandardCharsets.UTF_8));
            }

            Log.i(TAG, "Response Code: " + urlConnection.getResponseCode());

        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(MainActivity.this);
                    dlgAlert.setMessage("An exception occurred!");
                    dlgAlert.setTitle("HTTP Error");
                    dlgAlert.setPositiveButton("OK", null);
                    dlgAlert.setCancelable(true);
                    dlgAlert.create().show();
                }
            });

            e.printStackTrace();
        }
    }

    public String getIMEI() {
        String imei = "";

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(this.TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                imei = tm.getImei();
            } else {
                imei = tm.getDeviceId();
            }
        } else {
            Log.e(TAG, "Permission READ_PHONE_STATE is missing");
        }

        if (imei.compareToIgnoreCase("000000000000000") == 0) // emulator = random imei
            imei = "490154203237518";

        return imei;
    }

    public String getPhoneNumber() {
        if (getIMEI().compareTo("490154203237518") == 0) return "06805619653"; // emulator = use random phone number

        String number = "";

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            TelephonyManager tm = (TelephonyManager) MainActivity.this.getSystemService(MainActivity.this.TELEPHONY_SERVICE);
            number = tm.getLine1Number();
        } else {
            Log.e(TAG, "Permission READ_PHONE_STATE is missing");
        }

        if (number == null || !number.matches("^\\d+$"))
            number = "06805619653";

        return number;
    }
}