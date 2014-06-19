package br.com.guilhermeruiz.twittecodataca;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import twitter4j.DirectMessage;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;
import twitter4j.UserList;
import twitter4j.UserStreamListener;
import twitter4j.auth.AccessToken;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

//import com.evernote.client.android.EvernoteSession;
//import com.evernote.client.android.InvalidAuthenticationException;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadLisener;

public class TwitterApp extends Activity implements OnClickListener, OnInitListener {
	private static final String TAG = "T4JSample";

	private Button twitterButtonLogin;
	private Button evernoteButtonLogin;
	private Button getTweetButton;
	private TextView tweetText;
	private TextView arduinoConnectionStatusTextView;
	private TextView ttStreamConnectionStatusTextView;
	private ScrollView scrollView;
	private static Twitter twitter;
	private static RequestToken requestToken;
	private static SharedPreferences mSharedPreferences;
	private static TwitterStream twitterStream;
	private boolean isTwitterStreamRunning = false;
	private TextToSpeech textToSpeech;
	private Physicaloid mPhysicaloid;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_twitter);

		mPhysicaloid = new Physicaloid(this);
		connect.start();
		mSharedPreferences = getSharedPreferences(Const.PREFERENCE_NAME, MODE_PRIVATE);
		scrollView = (ScrollView) findViewById(R.id.scrollView);
		tweetText = (TextView) findViewById(R.id.tweetText);
		getTweetButton = (Button) findViewById(R.id.getTweet);
		getTweetButton.setOnClickListener(this);
		twitterButtonLogin = (Button) findViewById(R.id.twitterLogin);
		twitterButtonLogin.setOnClickListener(this);
		evernoteButtonLogin = (Button) findViewById(R.id.evernoteLogin);
		evernoteButtonLogin.setOnClickListener(this);
		arduinoConnectionStatusTextView = (TextView) findViewById(R.id.arduinoConnectionStatusTextView);
		ttStreamConnectionStatusTextView = (TextView) findViewById(R.id.ttStreamConnectionStatusTextView);

		textToSpeech = new TextToSpeech(TwitterApp.this, this);

		// Handle OAuth Callback
		Uri uri = getIntent().getData();
		if (uri != null && uri.toString().startsWith(Const.CALLBACK_URL)) {
			String verifier = uri.getQueryParameter(Const.IEXTRA_OAUTH_VERIFIER);
			try {
				AccessToken accessToken = twitter.getOAuthAccessToken(requestToken, verifier);
				Editor e = mSharedPreferences.edit();
				e.putString(Const.PREF_KEY_TOKEN, accessToken.getToken());
				e.putString(Const.PREF_KEY_SECRET, accessToken.getTokenSecret());
				e.commit();
			} catch (Exception e) {
				Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
			}
		}

		// ****************************************************************
		// Register intent filtered actions for device being attached or
		// detached
		IntentFilter filter = new IntentFilter();
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		registerReceiver(mUsbReceiver, filter);
		// ****************************************************************

	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			if (textToSpeech.isLanguageAvailable(Locale.US) == TextToSpeech.LANG_AVAILABLE)
				textToSpeech.setLanguage(new Locale("pt", "BR"));
		} else if (status == TextToSpeech.ERROR) {
		}
	}

	// ****************************************************************
	// Get intent when a USB device attached
	@Override
	protected void onNewIntent(Intent intent) {
		String action = intent.getAction();
		if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
			openDevice();
		}
	};

	// ****************************************************************

	// Thread to verify Smartphone-Arduino connection constantly
	Thread connect = new Thread() {
		public void run() {
			while (true) {
				try {
					if (!mPhysicaloid.isOpened()) {
						openDevice();
					}

					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	};

	// Get intent when a USB device detached
	BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				closeDevice();
			}
		}
	};

	private void openDevice() {
		if (!mPhysicaloid.isOpened()) {
			// default 9600bps
			if (mPhysicaloid.open()) {
				mPhysicaloid.addReadListener(new ReadLisener() {
					String readStr;

					// Callback when reading one or more size buffer
					@Override
					public void onRead(int size) {
						byte[] buf = new byte[size];

						mPhysicaloid.read(buf, size);
						try {
							readStr = new String(buf, "UTF-8");
						} catch (UnsupportedEncodingException e) {
							Log.e(TAG, e.toString());
							return;
						}
					}
				});
			}
		}
	}

	private void closeDevice() {
		if (mPhysicaloid.close()) {
			mPhysicaloid.clearReadListener();
		}
	}

	public void writeSerial(String data) {
		byte[] buf = data.getBytes();
		mPhysicaloid.write(buf, buf.length);
	}

	protected void onResume() {
		super.onResume();

		if (isConnected()) {
			String oauthAccessToken = mSharedPreferences.getString(Const.PREF_KEY_TOKEN, "");
			String oAuthAccessTokenSecret = mSharedPreferences.getString(Const.PREF_KEY_SECRET, "");

			ConfigurationBuilder confbuilder = new ConfigurationBuilder();
			Configuration conf = confbuilder.setOAuthConsumerKey(Const.TWITTER_CONSUMER_KEY).setOAuthConsumerSecret(Const.TWITTER_CONSUMER_SECRET).setOAuthAccessToken(oauthAccessToken).setOAuthAccessTokenSecret(oAuthAccessTokenSecret).build();
			twitterStream = new TwitterStreamFactory(conf).getInstance();
			// Starting streaming
			startStreamingTimeline();
			isTwitterStreamRunning = true;
			getTweetButton.setText("stop streaming");
			// -----
			twitterButtonLogin.setText(R.string.label_disconnect);
			getTweetButton.setEnabled(true);
		} else {
			twitterButtonLogin.setText(R.string.label_connect);
		}
	}

	private boolean isConnected() {
		return mSharedPreferences.getString(Const.PREF_KEY_TOKEN, null) != null;
	}

	private void askOAuth() {
		ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
		configurationBuilder.setOAuthConsumerKey(Const.TWITTER_CONSUMER_KEY);
		configurationBuilder.setOAuthConsumerSecret(Const.TWITTER_CONSUMER_SECRET);
		configurationBuilder.setUseSSL(true);
		Configuration configuration = configurationBuilder.build();
		twitter = new TwitterFactory(configuration).getInstance();
		try {
			requestToken = twitter.getOAuthRequestToken(Const.CALLBACK_URL);
			Toast.makeText(this, "Please authorize this app!", Toast.LENGTH_LONG).show();
			this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(requestToken.getAuthenticationURL())));
		} catch (TwitterException e) {
			e.printStackTrace();
		}
	}

	// Disconnect twitter account removing token and secret from preferences
	private void disconnectTwitter() {
		SharedPreferences.Editor editor = mSharedPreferences.edit();
		editor.remove(Const.PREF_KEY_TOKEN);
		editor.remove(Const.PREF_KEY_SECRET);
		editor.commit();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.twitterLogin:
			if (isConnected()) {
				disconnectTwitter();
				twitterButtonLogin.setText(R.string.label_connect);
			} else {
				askOAuth();
			}
			break;
		case R.id.getTweet:
			if (isTwitterStreamRunning) {
				stopStreamingTimeline();
				isTwitterStreamRunning = false;
				getTweetButton.setText("Start streaming");
			} else {
				startStreamingTimeline();
				isTwitterStreamRunning = true;
				getTweetButton.setText("Stop streaming");
			}
			break;
		case R.id.evernoteLogin:
			new EvernoteReq(TwitterApp.this, evernoteButtonLogin).execute();
			break;
		}
	}

	private void stopStreamingTimeline() {
		twitterStream.shutdown();
	}

	public void startStreamingTimeline() {
		UserStreamListener listener = new UserStreamListener() {

			@Override
			public void onDeletionNotice(StatusDeletionNotice arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onScrubGeo(long arg0, long arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onStatus(Status status) {
				// Split tweet
				ArrayList<String> tweetWords = new ArrayList<String>();
				Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(status.getText());
				while (m.find()) {
					tweetWords.add(String.valueOf(m.group(1)));
				}

				if (tweetWords.get(0).toLowerCase().equals("@" + Const.USERNAME.toLowerCase())) {
					twiteckonActionsHandler(tweetWords, true);
				} else {
					twiteckonActionsHandler(tweetWords, false);
				}

				final String tweet = "@" + status.getUser().getScreenName() + " : " + status.getText() + "\n";
				tweetText.post(new Runnable() {
					@Override
					public void run() {
						tweetText.append(tweet);
						scrollView.fullScroll(View.FOCUS_DOWN);
					}
				});
			}

			@Override
			public void onTrackLimitationNotice(int arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onException(Exception arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onBlock(User arg0, User arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onDeletionNotice(long arg0, long arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onDirectMessage(DirectMessage arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onFavorite(User arg0, User arg1, Status arg2) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onFollow(User arg0, User arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onFriendList(long[] arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUnblock(User arg0, User arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUnfavorite(User arg0, User arg1, Status arg2) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUserListCreation(User arg0, UserList arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUserListDeletion(User arg0, UserList arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUserListMemberAddition(User arg0, User arg1, UserList arg2) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUserListMemberDeletion(User arg0, User arg1, UserList arg2) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUserListSubscription(User arg0, User arg1, UserList arg2) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUserListUnsubscription(User arg0, User arg1, UserList arg2) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUserListUpdate(User arg0, UserList arg1) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onUserProfileUpdate(User arg0) {
				// TODO Auto-generated method stub
			}

			@Override
			public void onStallWarning(StallWarning arg0) {
				// TODO Auto-generated method stub

			}
		};
		twitterStream.addListener(listener);
		twitterStream.user();
	}

	public void twiteckonActionsHandler(List<String> tweetWords, boolean isGuest) {
		int indexCommand = 1;
		int indexSpeak = 2;
		if (isGuest) {
			indexCommand++;
      indexSpeak++;
    }
    String command = tweetWords.get(indexCommand).toLowerCase();
    System.out.println("COMANDOO " + command);
    switch (command) {
    case "#golbrasil":
      gritarGol.start();
      writeSerial("1");
      break;
    case "#cante":
      int[] musics = new int[] { R.raw.musica_0, R.raw.musica_1 };
      try {
        Random ran = new Random();
        int x = ran.nextInt(2);
        MediaPlayer som = MediaPlayer.create(TwitterApp.this, musics[x]);
        som.start();
      } catch (Exception e) {

      }
      break;
    case "#fale":
      writeSerial("2");
      if (!isGuest) {
        String message = tweetWords.get(indexSpeak).replace("\"", "");
        textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null);
      } else {
        String message = "Desculpe, só falo com o meu dono.";
        textToSpeech.speak(message, TextToSpeech.QUEUE_ADD, null);
      }
      break;
    case "#curiosidade":
			writeSerial("2");
      ArrayList<String>  lista = new ArrayList<String>();
			lista.add("Seleção brasileira é composta por novatos, nenhum jogador da Seleção Brasileira é campeão do mundo.");
			lista.add("Os 12 estádios da Copa preencheram os requisitos para conseguir o selo verde de sustentabilidade.");
			Random r = new Random();
			String msg = lista.get(r.nextInt(lista.size()));
			textToSpeech.speak(msg, TextToSpeech.QUEUE_ADD, null);
			break;
		}
	}

	Thread gritarGol = new Thread() {
		public void run() {
			MediaPlayer goalYell = MediaPlayer.create(TwitterApp.this, R.raw.gol);
			goalYell.start();
		}
	};
}