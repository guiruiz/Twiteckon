package br.com.guilhermeruiz.twittecodataca;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;


public class EvernoteReq extends AsyncTask<Void, Void, JSONArray> {
	private Context ctx;
	private int httpResponseStatusCode;
	private ProgressDialog loadingDialog;
	private Button btn;

	public EvernoteReq(Context context, Button btn) {
		super();
		ctx = context;
		btn = btn;
	}

	@Override
	protected void onPreExecute() {
		loadingDialog = ProgressDialog.show(ctx, "Autenticando", "Aguarde", true);
	}

	@Override
	protected JSONArray doInBackground(Void... arg0) {
		doRequest();

		
		return null;
	}

	@Override
	protected void onPostExecute(JSONArray result) {
			loadingDialog.dismiss();
	}

	public int doRequest() {
		String url = "http://guilhermeruiz.com.br/ever/createnotes.php";
		InputStream is = null;
		String result = "";
		int statusCode;
		try {
			HttpClient httpclient = new DefaultHttpClient();
			HttpGet httpget = new HttpGet(url);
			HttpResponse response = httpclient.execute(httpget);
			HttpEntity entity = response.getEntity();
			is = entity.getContent();
			statusCode = response.getStatusLine().getStatusCode();

		} catch (Exception e) {
			statusCode = -1;
			Log.e("log_tag", "Erro http " + e.toString());
		}
		return statusCode;
	}

}