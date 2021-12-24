package jadx.core.export;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : Administrator
 * @date : 2021/12/21
 * @description :
 */
public class ApplicationDetailsParams extends ApplicationParams {
	private List<String> normalPermissions = new ArrayList<>();
	private String packageName;
	private List<String> dangerousPermissions = new ArrayList<>();

	private List<String> androidDpList = new ArrayList<>();

	public ApplicationDetailsParams(String applicationLabel, Integer minSdkVersion, Integer targetSdkVersion, Integer versionCode, String versionName) {
		super(applicationLabel, minSdkVersion, targetSdkVersion, versionCode, versionName);
		initPermission();
	}

	private void initPermission() {
		androidDpList.add("android.permission.READ_CALENDAR");
		androidDpList.add("android.permission.WRITE_CALENDAR");
		androidDpList.add("android.permission.CAMERA");
		androidDpList.add("android.permission.READ_CONTACTS");
		androidDpList.add("android.permission.WRITE_CONTACTS");
		androidDpList.add("android.permission.GET_ACCOUNTS");
		androidDpList.add("android.permission.ACCESS_FINE_LOCATION");
		androidDpList.add("android.permission.ACCESS_COARSE_LOCATION");
		androidDpList.add("android.permission.RECORD_AUDIO");
		androidDpList.add("android.permission.READ_PHONE_STATE");
		androidDpList.add("android.permission.CALL_PHONE");
		androidDpList.add("android.permission.READ_CALL_LOG");
		androidDpList.add("android.permission.WRITE_CALL_LOG");
		androidDpList.add("android.permission.ADD_VOICEMAIL");
		androidDpList.add("android.permission.USE_SIP");
		androidDpList.add("android.permission.PROCESS_OUTGOING_CALLS");
		androidDpList.add("android.permission.BODY_SENSORS");
		androidDpList.add("android.permission.SEND_SMS");
		androidDpList.add("android.permission.RECEIVE_SMS");
		androidDpList.add("android.permission.READ_SMS");
		androidDpList.add("android.permission.RECEIVE_WAP_PUSH");
		androidDpList.add("android.permission.RECEIVE_MMS");
		androidDpList.add("android.permission.READ_EXTERNAL_STORAGE");
		androidDpList.add("android.permission.WRITE_EXTERNAL_STORAGE");
	}

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public List<String> getNormalPermissions() {
		return normalPermissions;
	}

	public List<String> getDangerousPermissions() {
		return dangerousPermissions;
	}

	public void addPermission(String perm) {
		if(androidDpList.contains(perm)) {
			dangerousPermissions.add(perm);
		} else {
			normalPermissions.add(perm);
		}
	}
}
