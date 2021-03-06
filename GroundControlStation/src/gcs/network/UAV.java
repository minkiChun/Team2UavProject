package gcs.network;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONObject;

import gcs.mission.FencePoint;
import gcs.mission.WayPoint;
import team2gcs.appmain.AppMainController;
import team2gcs.dialog.altdialogController;
import team2gcs.dialog.timeDialogController;
import team2gcs.leftpane.leftPaneController;

public class UAV implements Cloneable {
	public String systemStatus;	
	public String statusText;
	public String mode;
	public double batteryVoltage;
	public double batteryCurrent;
	public double batteryLevel;
	public double roll;
	public double pitch;
	public double yaw;
	public boolean armed;
	public double latitude;
	public double longitude;
	public double altitude;
	public double abs_altitude;
	public double gpsTime;
	public double gpsSatellite;
	public double heading;
	public double airSpeed;
	public double groundSpeed;	
	public double homeLat;
	public double homeLng;
	public static int nextWP;
	public double rangeFinderDistance;
	public double opticalFlowQuality;
	
	public int nextWaypointNo;
	public List<WayPoint> wayPoints;
	public double fenceEnable;
	public double fenceType;
	public double fenceAction;
	public double fenceRadius;
	public double fenceAltMax;
	public double fenceMargin;
	public double fenceTotal;
	public List<FencePoint> fencePoints;
	
	public boolean connected;
	private MqttClient mqttClient;
	
	public UAV() {
	}
	
	public void connect() {
		Thread thread = new Thread() {
			@Override
			public void run() {			
				try {
					mqttClient = new MqttClient("tcp://" + AppMainController.ip + ":" + AppMainController.port, MqttClient.generateClientId(), null);
					System.out.println(AppMainController.ip);
					mqttClient.setCallback(new MqttCallback() {
						String strJson;
						@Override //메세지가 도착했을때
						public void messageArrived(String topic, MqttMessage message) throws Exception {
							strJson = new String(message.getPayload());
							dataParsing(strJson);
							connected = true;
						}
						@Override
						public void deliveryComplete(IMqttDeliveryToken token) {
						}
						@Override
						public void connectionLost(Throwable e) {
							UAV.this.disconnect();
							e.printStackTrace();
						}
					});
					MqttConnectOptions mco = new MqttConnectOptions();
					mco.setConnectionTimeout(5);
					mqttClient.connect(mco);
					mqttClient.subscribe(Network.uavPubTopic);
					gcsHeartBeat();
					AppMainController.connectState = true;
				} catch (Exception e) {
					UAV.this.disconnect();
				}
			}
		};
		thread.setDaemon(true);
		thread.start();
	}
	
	public void disconnect() {
		try {
			//AppMainController.instance2.setStatus(new UAV());
			mqttClient.disconnect();
			mqttClient.close();
			System.out.println("종료");
		} catch (Exception e) {
		}
		connected = false;
	}
	
	private boolean misstionState = false;
	
	private void dataParsing(String strJson) {
		try {
			JSONObject jsonObject = new JSONObject(strJson);
			systemStatus = jsonObject.getString("system_status");
			statusText = jsonObject.getString("statustext");
			mode = jsonObject.getString("mode");
			batteryVoltage = jsonObject.getDouble("battery_voltage");
			batteryCurrent = jsonObject.getDouble("battery_current");
			try {
				batteryLevel = jsonObject.getDouble("battery_level");
			} catch(Exception e) {
				//SITL에서 가끔 battery_level 을 보내지 않아
				//시뮬레이션에서 가끔 NullPointerException 발생
			}
			roll = jsonObject.getDouble("roll");
			pitch = jsonObject.getDouble("pitch");
			yaw = jsonObject.getDouble("yaw");
			armed = jsonObject.getBoolean("armed");
			latitude = jsonObject.getDouble("latitude");
			longitude = jsonObject.getDouble("longitude");
			altitude = jsonObject.getDouble("altitude");
			try {
				abs_altitude = jsonObject.getDouble("abs_altitude");
			}catch (Exception e) {
			}
			heading = jsonObject.getDouble("heading");
			airSpeed = jsonObject.getDouble("airspeed");
			groundSpeed = jsonObject.getDouble("groundspeed");
			homeLat = jsonObject.getDouble("homeLat");
			homeLng = jsonObject.getDouble("homeLng");
			nextWaypointNo = jsonObject.getInt("next_waypoint_no");
			gpsTime = jsonObject.getDouble("time");
			gpsSatellite = jsonObject.getDouble("gps");
			//System.out.println(jsonObject.getDouble("time"));
			//System.out.println(jsonObject.getDouble("gps"));
			
			if(armed) AppMainController.instance2.statusMessage("UAV Armed.");
			else if(!armed) {
				AppMainController.instance2.statusMessage("UAV Disarmed.");
				AppMainController.instance2.setGps = false;
			}
			if(leftPaneController.instance.distance(AppMainController.gotoLat, AppMainController.gotoLng, 
				Network.getUav().latitude, Network.getUav().longitude, "meter") < 0.7 && (AppMainController.gotoLat != 0 && AppMainController.gotoLng != 0)) {
				AppMainController.instance2.statusMessage("Go to Completed.");
				AppMainController.gotoLat = 0;
				AppMainController.gotoLng = 0;
			}
			if(statusText.length() > 0){
				if(statusText.contains("EKF2")) AppMainController.instance2.statusMessage("EKF2 message.");
				else AppMainController.instance2.statusMessage(statusText);
			}
					
			// landNum에 해당하는 Mission에 도달시 Land 진행
			if(AppMainController.instance2.checkLand&&statusText.equals("Reached command #"+AppMainController.instance2.landNum)) {
				AppMainController.instance2.handleLand();
				AppMainController.instance2.uploadState = true;
			}
			// CargoWP 마크가 활성화 되어있을 때만 자동 미션 진행
			if(!armed && AppMainController.instance2.checkLand && AppMainController.instance2.uploadState) {
				AppMainController.instance2.checkLand = false;
				AppMainController.instance2.changeColor();
				AppMainController.instance2.uploadState = false;
				// 미션 재생성을 위한 List
				List<WayPoint> tList = new ArrayList<>();
				// Mission Clear
				missionUpload(tList);
				int i=1;
				for(WayPoint wp: AppMainController.instance2.list) {
					if(wp.no > AppMainController.instance2.landNum) {
						wp.setNo(i++);
						tList.add(wp);
						AppMainController.instance2.lastNum = wp.getNo();
					}
				}
				missionUpload(tList);
//				 DisArmed 되면 화물 내림
				AppMainController.instance2.handleCargoStop();
				// Arm 후에 바로 TakeOff처리시 Delay를 위한 Thread Sleep
				Thread thread = new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Thread.sleep(timeDialogController.missionTime*1000);
							arm();
							Thread.sleep(timeDialogController.missionTime*1000);
							// 다시 Armed 후 TakeOff 진행
							takeoff(altdialogController.alt);
							misstionState = true;
						} catch (InterruptedException e) {e.printStackTrace();}
					}
				});
				thread.start();
			}
			
			// MissionState가 True이고 TakeOff를 한 뒤 원하는 고도의 -0.5만큼 띄워졌으면 재정의된 미션을 수행
			if(misstionState && altitude >= altdialogController.alt -0.5) {
				missionStart();
				misstionState = false;
				AppMainController.instance2.landNum = -999;
			}
			
			// 새로 정의된 미션의 마지막 WayPoint에 도달하면 Land 수행
			if(!misstionState && statusText.equals("Reached command #"+AppMainController.instance2.lastNum)) {
				AppMainController.instance2.handleLand();
				AppMainController.instance2.lastNum = -999;
			}
			
			AppMainController.instance2.batterySet(batteryLevel);
			AppMainController.instance2.locationSet(latitude, longitude);
			
			try {
				rangeFinderDistance = jsonObject.getDouble("rangefinder_distance");
				opticalFlowQuality = jsonObject.getDouble("optical_flow_quality");
			} catch(Exception e) {
				rangeFinderDistance = 0;
				opticalFlowQuality = 0;
			}
			
			JSONArray jsonArrayWayPoints = jsonObject.getJSONArray("waypoints");
			List<WayPoint> listWayPoint = new ArrayList<WayPoint>();
			for(int i=0; i<jsonArrayWayPoints.length(); i++) {
				JSONObject jo = jsonArrayWayPoints.getJSONObject(i);
				WayPoint wp = new WayPoint();
				wp.no = i+1;
				wp.kind = jo.getString("kind");
				if(wp.kind.equals("takeoff")) {
					wp.altitude = jo.getDouble("alt");
				} else if(wp.kind.equals("waypoint")) {
 					wp.setLat(jo.getDouble("lat")+"");
					wp.setLng(jo.getDouble("lng")+"");
					wp.altitude = jo.getDouble("alt");
				} else if(wp.kind.equals("rtl")) {
				} else if(wp.kind.equals("jump")) {
 					wp.setJumpNo(jo.getInt("seq"));
					wp.setRepeatCount(jo.getInt("cnt"));
				} else if(wp.kind.equals("roi")) {
 					wp.setLat(jo.getDouble("lat")+"");
					wp.setLng(jo.getDouble("lng")+"");
				} else if(wp.kind.equals("arm")) {
 					wp.setLat(jo.getDouble("lat")+"");
					wp.setLng(jo.getDouble("lng")+"");
				} else if(wp.kind.equals("land")) {
 					wp.setLat(jo.getDouble("lat")+"");
					wp.setLng(jo.getDouble("lng")+"");
					wp.altitude = jo.getDouble("alt");
				}
				listWayPoint.add(wp);
			}
			wayPoints = listWayPoint;
			if(!listWayPoint.isEmpty()) {
				AppMainController.instance2.list = listWayPoint;
				AppMainController.instance2.setMission(listWayPoint);
			}
			
			JSONObject jsonObjectFenceInfo =  jsonObject.getJSONObject("fence_info");
			fenceEnable = jsonObjectFenceInfo.getDouble("fence_enable");
			try {
				fenceType = jsonObjectFenceInfo.getDouble("fence_type");
				fenceAction = jsonObjectFenceInfo.getDouble("fence_action");
				fenceRadius = jsonObjectFenceInfo.getDouble("fence_radius");
				fenceAltMax = jsonObjectFenceInfo.getDouble("fence_alt_max");
				fenceMargin = jsonObjectFenceInfo.getDouble("fence_margin");
				fenceTotal = jsonObjectFenceInfo.getDouble("fence_total");
				JSONArray jsonArrayFencePoints = jsonObjectFenceInfo.getJSONArray("fence_points");
				List<FencePoint> listFencePoint = new ArrayList<FencePoint>();
				for(int i=0; i<jsonArrayFencePoints.length(); i++) {
					JSONObject jo = jsonArrayFencePoints.getJSONObject(i);
					FencePoint fp = new FencePoint();
					fp.idx = jo.getInt("idx");
					fp.count = jo.getInt("count");
					fp.lat = jo.getDouble("lat");
					fp.lng = jo.getDouble("lng");
					listFencePoint.add(fp);
				}
				fencePoints = listFencePoint;
			} catch(Exception e) {
				fenceType = 0;
				fenceAction = 0;
				fenceRadius = 0;
				fenceAltMax = 0;
				fenceMargin = 0;
				fenceTotal = 0;
				fencePoints = new ArrayList<FencePoint>();
			}
			
			AppMainController.instance2.viewStatus(this);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}	
	
	public void send(String strJson) {
		if(connected) {
			Thread thread = new Thread() {
				@Override
				public void run() {
					try {
						MqttMessage message = new MqttMessage(strJson.getBytes());
						mqttClient.publish(Network.uavSubTopic, message);
					} catch(Exception e) {
						e.printStackTrace();
						UAV.this.disconnect();
					} 
				}
			};
			thread.setDaemon(true);
			thread.start();
		}
	}
	
	public void cargo(String command) {
		JSONObject jsonObject = new JSONObject();
		if(command.equals("cargoStart")) {
			jsonObject.put("command", "cargoStart");
		}else {
			jsonObject.put("command", "cargoStop");
		}		
		String strJson = jsonObject.toString();
		send(strJson);
		System.out.println(strJson);
	}
	
	public void arm() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "arm");
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void disarm() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "disarm");
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void takeoff(int height) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "takeoff");
		jsonObject.put("height", height);
		String strJson = jsonObject.toString();
		send(strJson);
	}	
	
	public void changeAlt(int height) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "changealt");
		jsonObject.put("height", height);
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void rtl() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "rtl");
		String strJson = jsonObject.toString();
		send(strJson);	
	}	
	
	public void land() {	
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "land");
		String strJson = jsonObject.toString();
		send(strJson);
	}	
	
	public void gotoStart(double latitude, double longitude, double altitude) {	
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "goto");
		jsonObject.put("latitude", latitude);
		jsonObject.put("longitude", longitude);
		jsonObject.put("altitude", altitude);
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void missionUpload(List<WayPoint> list) {	
		JSONObject root = new JSONObject();
		root.put("command", "mission_upload");
		JSONArray jsonArray = new JSONArray();
		for(WayPoint wp : list) {
			JSONObject jo = new JSONObject();
			jo.put("kind", wp.kind);
			if(wp.getLat() != null) jo.put("lat", Double.parseDouble(wp.getLat()));
			if(wp.getLng() != null) jo.put("lng", Double.parseDouble(wp.getLng()));
			jo.put("alt", wp.altitude);
			jo.put("seq", wp.jumpNo);
			jo.put("cnt", wp.repeatCount);
			jsonArray.put(jo);
		}
		root.put("waypoints", jsonArray);
		String strJson = root.toString();
		send(strJson);
	}
	
	public void missionDownload() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "mission_download");
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void missionStart() {	
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "mission_start");
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void missionStop() {	
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "mission_stop");
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void fenceEnable() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "fence_enable");
		String strJson = jsonObject.toString();
		send(strJson);
	}	
	
	public void fenceDisable() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "fence_disable");
		String strJson = jsonObject.toString();
		send(strJson);
	}	
	
	public void fenceUpload(String jsonFencePoints) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "fence_upload");
		jsonObject.put("fence_type", 7); //4:polygon, 7:All=polygon+radius+alt_max
		jsonObject.put("fence_action", 1); //RTL
		jsonObject.put("fence_radius", 500);
		jsonObject.put("fence_alt_max", 100);
		jsonObject.put("fence_margin", 5);
		JSONArray jsonArrayFencePoints = new JSONArray(jsonFencePoints);
		jsonObject.put("fence_total", jsonArrayFencePoints.length());
		jsonObject.put("points", jsonArrayFencePoints);
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void fenceDownload() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "fence_download");
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void fenceClear() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "fence_clear");
		String strJson = jsonObject.toString();
		send(strJson);
	}
	public void gcsHeartBeat() {
		Thread thread = new Thread() {
			@Override
			public void run() {
				while(true) {
					try {
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("command", "gcs_connect");
						jsonObject.put("status", "true");
						String strJson = jsonObject.toString();
						send(strJson);
						Thread.sleep(1000);
					}catch(Exception e) {}
				}
			}
		};
		thread.start();
	}
	
	public void move(double velocityX, double velocityY, double velocityZ, double duration) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "move");
		jsonObject.put("velocity_x", velocityX);
		jsonObject.put("velocity_y", velocityY);
		jsonObject.put("velocity_z", velocityZ);
		jsonObject.put("duration", duration);
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
	public void changeHeading(double heading) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("command", "change_heading");
		jsonObject.put("heading", heading);
		String strJson = jsonObject.toString();
		send(strJson);
	}
	
}