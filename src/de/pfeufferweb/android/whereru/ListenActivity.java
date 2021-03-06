package de.pfeufferweb.android.whereru;

import java.util.Date;
import java.util.List;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;
import android.widget.TwoLineListItem;
import de.pfeufferweb.android.whereru.repository.LocationRequest;
import de.pfeufferweb.android.whereru.repository.RequestRepository;
import de.pfeufferweb.android.whereru.repository.Status;

public class ListenActivity extends ListActivity {

	private class RequestArrayAdapter extends ArrayAdapter<LocationRequest> {

		RequestArrayAdapter(Context context, List<LocationRequest> requests) {
			super(context, android.R.layout.simple_list_item_2, requests);
		}

		@SuppressWarnings("deprecation")
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TwoLineListItem row;
			if (convertView == null) {
				LayoutInflater inflater = (LayoutInflater) getApplicationContext()
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				row = (TwoLineListItem) inflater.inflate(
						android.R.layout.simple_list_item_2, null);
			} else {
				row = (TwoLineListItem) convertView;
			}
			LocationRequest request = getItem(position);
			formatListItem(row, request);
			return row;
		}

		@SuppressWarnings("deprecation")
		private void formatListItem(TwoLineListItem row, LocationRequest request) {
			String status = stringForStatus(request.getStatus());
			String requester = request.getRequester();
			String dateOfRequest = DateFormat
					.getDateFormat(ListenActivity.this).format(
							new Date(request.getTime()));
			String timeOfRequest = DateFormat
					.getTimeFormat(ListenActivity.this).format(
							new Date(request.getTime()));
			row.getText1().setTextColor(
					getResources().getColor(android.R.color.black));
			row.getText2().setTextColor(
					getResources()
							.getColor(colorForStatus(request.getStatus())));
			row.getText1().setText(
					getString(R.string.locationListEntryHeader, dateOfRequest,
							timeOfRequest));
			row.getText2().setText(
					getString(R.string.locationListEntryFooter, requester,
							status));
		}

		private String stringForStatus(Status status) {
			switch (status) {
			case NO_LOCATION:
				return getString(R.string.statusNoLocation);
			case SUCCESS:
				return getString(R.string.statusLocationFound);
			case RUNNING:
				return getString(R.string.statusRunning);
			case NO_GPS:
				return getString(R.string.statusNoGps);
			case ABORTED:
				return getString(R.string.statusAborted);
			case NETWORK:
				return getString(R.string.statusNetwork);
			case NETWORK_NO_GPS:
				return getString(R.string.statusNetworkNoGps);
			default:
				throw new IllegalArgumentException("unknown status: " + status);
			}
		}

		private int colorForStatus(Status status) {
			switch (status) {
			case NO_LOCATION:
				return R.color.noFix;
			case SUCCESS:
				return R.color.locationFound;
			case RUNNING:
				return R.color.running;
			case NO_GPS:
				return R.color.noGps;
			case ABORTED:
				return R.color.aborted;
			case NETWORK:
				return R.color.locationFound;
			case NETWORK_NO_GPS:
				return R.color.noGps;
			default:
				throw new IllegalArgumentException("unknown status: " + status);
			}
		}
	}

	private RequestRepository repository;
	private final Settings settings = new Settings(this);

	private TextView triggerTextView;
	private TextView triggerOnOffTextView;
	private TextView historyTextView;
	private ToggleButton toggleButton;

	private final BroadcastReceiver newRequestReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			fillRequests();
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_listen);
		triggerTextView = (TextView) findViewById(R.id.textViewTrigger);

		triggerOnOffTextView = (TextView) findViewById(R.id.textViewTriggerOnOff);

		historyTextView = (TextView) findViewById(R.id.textViewHistory);

		toggleButton = (ToggleButton) findViewById(R.id.activateToggleButton);
		toggleButton.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView,
					boolean isChecked) {
				settings.setActive(isChecked);
				setTriggerActivated(isChecked);
			}
		});

		repository = new RequestRepository(this);

		this.getListView().setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				LocationRequest request = (LocationRequest) getListAdapter()
						.getItem(position);
				if (request.getLocation() != null) {
					openMaps(request);
				}
			}

			private void openMaps(LocationRequest request) {
				String uri = getString(R.string.geoUrl, (request.getLocation()
						.getLatitude()),
						(request.getLocation().getLongitude()), f(request
								.getLocation().getLatitude()), f(request
								.getLocation().getLongitude()));
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
			}

			private String f(double d) {
				return new DegreeFormatter().format(d);
			}
		});
		registerForContextMenu(getListView());

		ListenActivityBroadcast.register(this, newRequestReceiver);

		fillRequests();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		if (v == getListView() && settings.getActive()) {
			menu.add(getString(R.string.contextSendAgain))
					.setOnMenuItemClickListener(new OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							LocationRequest request = getRequest(item);
							sendAgain(request);
							return true;
						}
					});
			menu.add(getString(R.string.contextCall))
					.setOnMenuItemClickListener(new OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							LocationRequest request = getRequest(item);
							call(request);
							return true;
						}
					});
			menu.add(getString(R.string.contextSendSms))
					.setOnMenuItemClickListener(new OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							LocationRequest request = getRequest(item);
							sendSms(request);
							return true;
						}
					});
		}
	}

	private void sendAgain(LocationRequest request) {
		Intent startService = new Intent(ListenActivity.this, SendService.class);
		startService.putExtra("receiver", request.getRequester());
		startService.putExtra("notificationId", -1);
		startService.putExtra("seconds", settings.getSeconds());
		ListenActivity.this.startService(startService);
	}

	private void call(LocationRequest request) {
		String uri = "tel:" + request.getRequester();
		Intent intent = new Intent(Intent.ACTION_DIAL);
		intent.setData(Uri.parse(uri));
		startActivity(intent);
	}

	private void sendSms(LocationRequest request) {
		String uri = "sms:" + request.getRequester();
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(uri));
		startActivity(intent);
	}

	@Override
	protected void onStart() {
		boolean active = settings.getActive();
		toggleButton.setChecked(active);
		triggerTextView.setText(settings.getRequestText());
		setTriggerActivated(active);
		fillRequests();
		Log.d("ListenActivity", "" + settings.getSeconds());
		super.onStart();
	}

	private void fillRequests() {
		final List<LocationRequest> requests = repository.getAllRequests();

		if (requests.isEmpty()) {
			historyTextView.setText(getString(R.string.textNoRequests));
		} else {
			historyTextView.setText(getString(R.string.textRequests));
		}
		ArrayAdapter<LocationRequest> adapter = new RequestArrayAdapter(this,
				requests);
		setListAdapter(adapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_listen, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.preferences:
			startActivity(new Intent(this, MainPreferenceActivity.class));
			break;
		case R.id.clearRequests:
			clearRequests();
			fillRequests();
			break;
		case R.id.about:
			new AlertDialog.Builder(this).setTitle(R.string.aboutTitle)
					.setMessage(R.string.aboutText).show();
			break;
		default:
			break;
		}
		return false;
	}

	private void clearRequests() {
		this.repository.deleteAllRequests();
		this.fillRequests();
	}

	@Override
	protected void onDestroy() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(
				newRequestReceiver);
		super.onDestroy();
	}

	private void setTriggerActivated(boolean activated) {
		triggerTextView
				.setVisibility(activated ? View.VISIBLE : View.INVISIBLE);
		triggerOnOffTextView
				.setText(getString(activated ? R.string.textTriggerOn
						: R.string.textTriggerOff));
	}

	private LocationRequest getRequest(MenuItem item) {
		int index = ((AdapterContextMenuInfo) item.getMenuInfo()).position;
		return (LocationRequest) getListAdapter().getItem(index);
	}
}
