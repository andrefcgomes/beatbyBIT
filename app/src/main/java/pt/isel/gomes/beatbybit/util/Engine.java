package pt.isel.gomes.beatbybit.util;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.session.AppKeyPair;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pt.isel.gomes.beatbybit.util.comm.BITalinoDevice;
import pt.isel.gomes.beatbybit.util.comm.BITalinoException;
import pt.isel.gomes.beatbybit.util.comm.BITalinoFrame;

public class Engine{

    private final static String APP_KEY = "un624qhagsgq8wb";
    private final static String APP_SECRET = "wid188gkonsbj62";

    private static final String PROVIDER_NAME = "com.example.provider.GeneralProvider";
    private static final String fileURL = "content://" + PROVIDER_NAME + "/fileTable";
    private static final String maleURL = "content://" + PROVIDER_NAME + "/maleTable";
    private static final String femaleURL = "content://" + PROVIDER_NAME + "/femaleTable";
    private final Uri fileURI = Uri.parse(fileURL);
    private final Uri maleURI = Uri.parse(maleURL);
    private final Uri femaleURI = Uri.parse(femaleURL);

    private int sampleRate = 100;

    public BITalinoDevice getBit() {
        return bit;
    }

    private BITalinoDevice bit;
    private String macAddress;
    private boolean connection = false;
    private static final UUID MY_UUID = UUID
            .fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetooth;
    private boolean[] tags = {false, false, false, false, false, false};
    private static Engine engine;

    public Engine() {
        if (macAddress == null) {
            setMac("00:00:00:00:00:00");
        } else
            checkBluetooth();
        try {
            bit = new BITalinoDevice(sampleRate, new int[]{0, 1, 2, 3, 4, 5});
        } catch (BITalinoException e) {
            e.printStackTrace();
        }
    }

    public static Engine getInstance() {
        if (engine == null)
            engine = new Engine();

        return engine;
    }

    public Uri getFileURI() {
        return fileURI;
    }

    public Uri getMaleURI() {
        return maleURI;
    }

    public Uri getFemaleURI() {
        return femaleURI;
    }

    public String getProvider() {
        return PROVIDER_NAME;
    }


    public UUID getUUID() {
        return MY_UUID;
    }

    public void toggleTag(int idx) {
        tags[idx] = !tags[idx];

    }

    public String getTags() {
        tags[0] = true;
        String result = "[" + tags[0];
        for (int i = 1; i < tags.length; i++) {
            result += "," + tags[i];
        }
        result += "]";
        //Log.i("TAGS", result);
        return result;
    }

    public boolean setMac(String mac) {
        Pattern p = Pattern.compile("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$");
        Matcher m = p.matcher(mac);
        boolean result = m.find();
        if (result)
            macAddress = mac;
        return result;
    }

    public boolean checkBluetooth() {
        bluetooth = BluetoothAdapter.getDefaultAdapter();

        if (bluetooth != null && bluetooth.isEnabled()) {
            Log.i("SEARCHING: ", getMacAddress());
            BluetoothDevice bita = bluetooth.getRemoteDevice(getMacAddress().toUpperCase());
            Log.i("FOUND: ", String.valueOf(bita != null));
            if (bita != null) {
                BluetoothSocket socket;
                try {
                    bluetooth.cancelDiscovery();
                    socket = bita.createRfcommSocketToServiceRecord(getUUID());
                    socket.connect();
                    open(socket.getInputStream(), socket.getOutputStream());
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }


    public BITalinoFrame[] read(int samples) throws BITalinoException {
        return bit.read(samples);
    }

    public void open(InputStream is, OutputStream os) throws BITalinoException {
        bit.open(is, os);
    }

    public DropboxAPI<AndroidAuthSession> getDropboxAPI(Context c) {
        AppKeyPair appKeys = new AppKeyPair(APP_KEY, APP_SECRET);
        AndroidAuthSession session = new AndroidAuthSession(appKeys);
        DropboxAPI<AndroidAuthSession> mDBApi = new DropboxAPI<>(session);
        SharedPreferences sharedpreferences = PreferenceManager.getDefaultSharedPreferences(c);
        String token = sharedpreferences.getString("token", null);
        if (token == null) {
            mDBApi.getSession().startOAuth2Authentication(c);
            SharedPreferences.Editor editor = sharedpreferences.edit();
            editor.putString("token", token);
            editor.apply();
            return mDBApi;
        }
        mDBApi.getSession().setOAuth2AccessToken(token);
        return mDBApi;
    }


    public String[] createFile(Cursor cursor) {
        String[] values = new String[cursor.getCount()];
        cursor.moveToFirst();
        for (int i = 0; i < cursor.getCount(); i++) {
            String line = "";
            line += cursor.getString(0);
            for (int j = 1; j < cursor.getColumnCount(); j++) {
                line += "," + cursor.getString(j);
            }
            values[i] = line;
            cursor.moveToNext();
        }
        cursor.close();
        return values;
    }

    public void writeToFile(String file, String[] data) {
        File dir = getRootDir();
        File f = new File(dir + "/" + file);
        try {
            FileOutputStream out = new FileOutputStream(f);
            for (String s : data) {
                s += "\n";
                out.write(s.getBytes());
            }
            out.close();
        } catch (IOException e) {

        }

    }

    public String getMacAddress() {
        return macAddress;
    }

    public String[] getFiles() {
        File dir = getRootDir();
        return dir.list();
    }

    public void testCloud(DropboxAPI<AndroidAuthSession> dropbox) throws IOException, DropboxException {
        File dir = getRootDir();
        String[] values = getFiles();
        for (String s : values) {
            File f = new File(dir, s);
            FileInputStream inputStream = new FileInputStream(f);
            dropbox.putFileOverwrite(s, inputStream, f.length(), null);
        }
    }

    public boolean testCon() throws BITalinoException {
        return bit.version() != null;
    }

    public void setSampleRate(int choice) throws BITalinoException {
        this.sampleRate = choice;
        this.bit = new BITalinoDevice(this.sampleRate, new int[]{0, 1, 2, 3, 4, 5});
    }

    public int getSampleRate() {
        return this.sampleRate;
    }

    public boolean conStatus() {
        return this.connection;
    }

    public void setConStatus(boolean con) {
        this.connection = con;
    }

    public void toastStatus(Context c) {
        Toast.makeText(c, String.valueOf(this.connection), Toast.LENGTH_LONG);
    }

    public File getRootDir() {
        File root = Environment.getExternalStorageDirectory();
        File beat = new File(root, "beat");
        if (!beat.exists()) {
            beat.mkdirs();
        }
        return beat;
    }
}
