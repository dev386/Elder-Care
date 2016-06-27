#include <HttpClient.h>
#include <LTask.h>
#include <LWiFi.h>
#include <LDateTime.h>
#include <LWiFiClient.h>
#include <LGPS.h>
#include <mthread.h>
#include <LGSM.h>
//3-axis
#include <Wire.h>
#include "MMA7660.h"

#define WIFI_AUTH LWIFI_WPA  // choose from LWIFI_OPEN, LWIFI_WPA, or LWIFI_WEP.

#define DEVICEID "DhEOz10B" // Input your deviceId
#define DEVICEKEY "4U0aJ1bTUQqLpjt0" // Input your deviceKey
#define SITE_URL "api.mediatek.com"
#define TRYTIME 5

#define LED_PIN 13
#define BTN_PIN 10

#define BLE_THREAD 1
#define HEART_RATE_THREAD 2
#define FALL_ALERT_THREAD 3
#define WIFI_STATUS 4

// ===3-axis accelemeter===
MMA7660 accelemeter;

// ===Heart rate===
#define HEART_PIN A0
const double alpha = 0.5;              // smoothing
const double beta = 0.5;                // find peak
const int period = 20;                  // sample delay period
unsigned long endTime = millis() - 55000;                  //time when the sense heart rate ends

// ===Cancel Sensor===
#define TOUCH_PIN 8

// ===BLE===
const char SET_PHONE = 'a';
const char SET_WIFI_SSID = 'b';
const char SET_WIFI_PWD = 'c';
#define MAXSTR 50
char r;
char *phoneNum, *wifiSSID, *wifiPwd;

// ===Wifi===
unsigned int rtc;
unsigned int lrtc;
unsigned int rtc1;
unsigned int lrtc1;
char port[4] = {0};
char connection_info[21] = {0};
char ip[21] = {0};
int portnum;
int val = 0;
String tcpdata = String(DEVICEID) + "," + String(DEVICEKEY) + ",";
String upload_led;
String tcpcmd_led_on = "LED_Control,1";
String tcpcmd_led_off = "LED_Control,0";

// ===GPS===
gpsSentenceInfoStruct info;
char buff[256];

// ===Thread Class===

class MultiThread : public Thread
{
  public:
    MultiThread(int id);
  protected:
    bool loop();
  private:
    void listenBLE();
    String senseHeartRate();
    void senseFallGPS();
    void showWifiStatus();

    void sendHeartRate();
    void sendFallGPS();
    void sendCancel();
    String cancelGPS();
    char* readStr();
    void connectAP();
    void getconnectInfo();
    void pushDataToMCS(String data);
    void getResponse();
    String getGPS();
    unsigned char getComma(unsigned char num, const char *str);
    double getDoubleNumber(const char *s);
    double getIntNumber(const char *s);
    char* parseGPGGA(const char* GPGGAstr);
    void callFamily();
    int id;

    LWiFiClient c2;
};

MultiThread::MultiThread(int id) {
  this->id = id;
}

bool MultiThread::loop()
{



  // Die if requested:
  if (kill_flag)
    return false;
  switch (id) {
    case BLE_THREAD:
      listenBLE();
      break;
    case HEART_RATE_THREAD:
      if(LWiFi.status() == LWIFI_STATUS_CONNECTED)
        sendHeartRate();
      break;
    case FALL_ALERT_THREAD:
      if(LWiFi.status() == LWIFI_STATUS_CONNECTED)
        senseFallGPS();
      break;
    case WIFI_STATUS:
      showWifiStatus();
      break;
  }
  return true;

}

// ===main===
void setup() {
  //Serial
  Serial.begin(9600);   // 與電腦序列埠連線
  while (!Serial);

  //I/O
  pinMode(LED_PIN, OUTPUT);
  pinMode(BTN_PIN, INPUT);
  pinMode(TOUCH_PIN, INPUT);
  randomSeed(analogRead(0));

  //BLE
  Serial.println("BT is ready!");
  Serial1.begin(9600);

  
  phoneNum = '\0';
  wifiSSID = '\0';
  wifiPwd = '\0';

  //Wifi
  LTask.begin();
  LWiFi.begin();

  //GPS
  LGPS.powerOn();
  Serial.println("LGPS Power on, and waiting ...");
  delay(3000);

  digitalWrite(LED_PIN, LOW);
  Serial.println("Setting done! Start looping!");

  // start multi-thread
  main_thread_list->add_thread(new MultiThread(BLE_THREAD));
  main_thread_list->add_thread(new MultiThread(HEART_RATE_THREAD));
  main_thread_list->add_thread(new MultiThread(FALL_ALERT_THREAD));
  main_thread_list->add_thread(new MultiThread(WIFI_STATUS));
}

void MultiThread::senseFallGPS()
{
  float ax, ay, az;
  accelemeter.getAcceleration(&ax, &ay, &az);
  //計算strength vector magnitude
  double SVM = sqrt(pow(ax, 2) + pow(ay, 2) + pow(az, 2));
  //如果SVM>3.2，表示有跌倒的可能
  Serial.print("SVM :");
  Serial.println(SVM);
  if (SVM >= 3.2)
  {
    sendFallGPS();
    delay(1000);
    //10秒內等待觸摸取消鍵則取消緊急通報
    int cancel = digitalRead(TOUCH_PIN);
    unsigned long startTime = millis();         // 記錄開始時間
    while (millis() - startTime < 10000) {      // sense 10 seconds
      if (TOUCH_PIN == 1)
      {
        sendCancel();
      }
    }
  }
}

void MultiThread::listenBLE() {
  // 若收到「序列埠監控視窗」的資料，則送到藍牙模組
  if (Serial.available()) {
    r = Serial.read();
    Serial1.println(r);
  }

  // 若收到藍牙模組的資料，則送到「序列埠監控視窗」
  if (Serial1.available()) {
    r = Serial1.read();
    Serial.println(r);
    switch (r) {
      case SET_PHONE:
        phoneNum = readStr();
        break;
      case SET_WIFI_SSID:
        wifiSSID = readStr();
        break;
      case SET_WIFI_PWD:
        wifiPwd = readStr();
        connectAP();
        break;
    }
  }
}

String MultiThread::senseHeartRate() {
  int count = 0;
  double oldValue = 0;
  double oldChange = 0;

  unsigned long startTime = millis();
  while (millis() - startTime < 5000) {      // sense 5 seconds
    int rawValue = analogRead(HEART_PIN);
    double value = alpha * oldValue + (1 - alpha) * rawValue; //smoothing value

    //find peak
    double change = value - oldValue;
    if (change > beta && oldChange < -beta) { // heart beat
      count = count + 1;
    }

    oldValue = value;
    oldChange = change;
    delay(period);
  }
   endTime = millis();
  return String((count * 12));
}

void MultiThread::sendCancel() {
  Serial.println("~~~send Cancel~~~");
  String cancel_gps = cancelGPS();
  String data = "FallAlert,," + cancel_gps;
  pushDataToMCS(data);
  Serial.println("~~~send Cancel end~~~");
}

String MultiThread::cancelGPS()
{
  String gps = getGPS();
  String cancel_gps;
  char* gpsChar;
  gps.toCharArray(gpsChar, gps.length());
  char* command = strtok(gpsChar, ",");
  while (command != 0)
  {
    cancel_gps += command;
    command = strtok(0, ",");
  }
  cancel_gps += "-999";
  return cancel_gps;
}

void MultiThread::showWifiStatus() {
  if (LWiFi.status() == LWIFI_STATUS_CONNECTED)
    digitalWrite(LED_PIN, HIGH);
  else
    digitalWrite(LED_PIN, LOW);
}

void MultiThread::sendHeartRate() {
  if(millis() - endTime >= 55000) //sense 5 seconds, stop sensing for the next 55 seconds
  {
    Serial.println("~~~send heartBeat~~~");
    String rate = senseHeartRate();
    String data = "HeartRate,," + rate;
    Serial.print("data is");
    Serial.println(data);
    pushDataToMCS(data);
    Serial.println("~~~send heartBeat end~~~");
  }
}

void MultiThread::sendFallGPS() {
  Serial.println("~~~send Fall GPS");
  String gps = getGPS();
  String data = "FallAlert,," + gps;
  pushDataToMCS(data);
  Serial.println("~~~send GPS end~~~");
}

// ******* BLE other function******
char* MultiThread::readStr() {
  char* rev = (char*)malloc(MAXSTR * sizeof(char));
  char* tmp = rev;
  int len = 0;
  do {
    r = Serial1.read();
    len = len * 10 + r - '0';

  } while (r != '!');
  len = (len - '!' + '0') / 10;
  Serial.println(len);
  int i;
  for (i = 0; i < len; ++i) {
    r = Serial1.read();
    /*
      int num=r;
      Serial.print("#");
      Serial.print(num);
    */
    *rev = r;
    rev++;
  }
  *rev = '\0';
  rev = tmp;
  Serial.print("!!!");
  Serial.println(rev);
  return rev;
}

// ****** Wifi other function**********
void MultiThread::connectAP() {
  Serial.println("Connecting to AP");
  int retryCount = 0;
  while (0 == LWiFi.connect(wifiSSID, LWiFiLoginInfo(WIFI_AUTH, wifiPwd)) && retryCount < TRYTIME)
  {
    delay(1000);
    retryCount++;
  }
  if (retryCount == TRYTIME) {
    Serial.println("connecting timeout! Please check the Wifi ssid and pwd");
    return;
  }

  Serial.println("calling connection");

  while (!c2.connect(SITE_URL, 80))
  {
    Serial.println("Re-Connecting to WebSite");
    delay(1000);
  }
  delay(100);

  Serial.println("---------mcs information--------");
  getconnectInfo();
  Serial.println("---------------------------------");

}

void MultiThread::getconnectInfo() {
  //calling RESTful API to get TCP socket connection
  c2.print("GET /mcs/v2/devices/");
  c2.print(DEVICEID);
  c2.println("/connections.csv HTTP/1.1");
  c2.print("Host: ");
  c2.println(SITE_URL);
  c2.print("deviceKey: ");
  c2.println(DEVICEKEY);
  c2.println("Connection: close");
  c2.println();

  getResponse();
  char c;
  int ipcount = 0;
  int count = 0;
  int separater = 0;
  while (c2)
  {
    int v = c2.read();
    if (v != -1)
    {
      c = v;
      Serial.print(c);
      connection_info[ipcount] = c;
      if (c == ',')
        separater = ipcount;
      ipcount++;
    }
    else
    {
      Serial.println("no more content, disconnect");
      c2.stop();

    }

  }
  Serial.print("The connection info: ");
  Serial.println(connection_info);
  int i;
  for (i = 0; i < separater; i++)
  { ip[i] = connection_info[i];
  }
  int j = 0;
  separater++;
  for (i = separater; i < 21 && j < 5; i++)
  { port[j] = connection_info[i];
    j++;
  }
  Serial.println("The TCP Socket connection instructions:");
  Serial.print("IP: ");
  Serial.println(ip);
  Serial.print("Port: ");
  Serial.println(port);
  portnum = atoi (port);

} //getconnectInfo

void MultiThread::pushDataToMCS(String data)
{
  int retryCount = 0;
  while ((!c2.connect(SITE_URL, 80)) && retryCount < TRYTIME )
  {
    Serial.println("Re-connecting to Website");
    delay(1000);
    retryCount++;
  }
  if (retryCount == TRYTIME) {
    Serial.println("network timeout");
    //TODO: show user the error
    return;
  }

  Serial.print("=>");
  Serial.println(data);

  int dataLength = data.length();
  c2.print("POST /mcs/v2/devices/");
  c2.print(DEVICEID);
  c2.println("/datapoints.csv HTTP/1.1");
  c2.print("Host: ");
  c2.println(SITE_URL);
  c2.print("deviceKey: ");
  c2.println(DEVICEKEY);
  c2.print("Content-Length: ");
  c2.println(dataLength);
  c2.println("Content-Type: text/csv");
  c2.println("Connection: close");
  c2.println();
  c2.println(data);

  getResponse();

  while (c2)
  {
    int v = c2.read();
    if (v != -1)
    {
      Serial.print(char(v));
    }
    else
    {
      Serial.println("no more content, disconnect");
      c2.stop();

    }

  }
}

void MultiThread::getResponse() {
  delay(500);

  int errorcount = 0;
  while (!c2.available())
  {
    Serial.print("waiting HTTP response: ");
    Serial.println(errorcount);
    errorcount += 1;
    if (errorcount > 10) {
      c2.stop();
      return;
    }
    delay(100);
  }

  HttpClient http(c2);
  int err = http.skipResponseHeaders();

  int bodyLen = http.contentLength();
  Serial.print("Content length is: ");
  Serial.println(bodyLen);
  Serial.println();
}

// ***** GPS *********
String MultiThread::getGPS() {
  LGPS.getData(&info);
  Serial.println((char*)info.GPGGA);
  return parseGPGGA((const char*)info.GPGGA);
}

unsigned char MultiThread::getComma(unsigned char num, const char *str)
{
  unsigned char i, j = 0;
  int len = strlen(str);
  for (i = 0; i < len; i ++)
  {
    if (str[i] == ',')
      j++;
    if (j == num)
      return i + 1;
  }
  return 0;
}

double MultiThread::getDoubleNumber(const char *s)
{
  char buf[10];
  unsigned char i;
  double rev;

  i = getComma(1, s);
  i = i - 1;
  strncpy(buf, s, i);
  buf[i] = 0;
  rev = atof(buf);
  return rev;
}

double MultiThread::getIntNumber(const char *s)
{
  char buf[10];
  unsigned char i;
  double rev;

  i = getComma(1, s);
  i = i - 1;
  strncpy(buf, s, i);
  buf[i] = 0;
  rev = atoi(buf);
  return rev;
}

char* MultiThread::parseGPGGA(const char* GPGGAstr)
{
  /* Refer to http://www.gpsinformation.org/dale/nmea.htm#GGA
     Sample data: $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47
     Where:
      GGA          Global Positioning System Fix Data
      123519       Fix taken at 12:35:19 UTC
      4807.038,N   Latitude 48 deg 07.038' N
      01131.000,E  Longitude 11 deg 31.000' E
      1            Fix quality: 0 = invalid
                                1 = GPS fix (SPS)
                                2 = DGPS fix
                                3 = PPS fix
                                4 = Real Time Kinematic
                                5 = Float RTK
                                6 = estimated (dead reckoning) (2.3 feature)
                                7 = Manual input mode
                                8 = Simulation mode
      08           Number of satellites being tracked
      0.9          Horizontal dilution of position
      545.4,M      Altitude, Meters, above mean sea level
      46.9,M       Height of geoid (mean sea level) above WGS84
                       ellipsoid
      (empty field) time in seconds since last DGPS update
      (empty field) DGPS station ID number
   *  *47          the checksum data, always begins with *
  */
  double latitude;
  double longitude;
  double altitude;
  int tmp, lat_deg, lon_deg;
  //int hour, minute, second, num ;
  if (GPGGAstr[0] == '$')
  {
    tmp = getComma(2, GPGGAstr);
    latitude = getDoubleNumber(&GPGGAstr[tmp]);
    tmp = getComma(4, GPGGAstr);
    longitude = getDoubleNumber(&GPGGAstr[tmp]);
    tmp = getComma(11, GPGGAstr);
    altitude = getDoubleNumber(&GPGGAstr[tmp]);

    //convert GPGGA(degree-mins-secs) to true decimal-degrees
    lat_deg = int(latitude / 100);
    lon_deg = int(longitude / 100);
    latitude = lat_deg + (latitude - lat_deg * 100) / 60;
    longitude = lon_deg + (longitude - lon_deg * 100) / 60;

    sprintf(buff, "%.6f,%.6f,%.4f", latitude, longitude, altitude);
    Serial.println(buff);

  }
  else
  {
    Serial.println("Not get data");
  }
  return buff;
}

void MultiThread::callFamily() {
  Serial.print("Calling to : ");
  Serial.println(phoneNum);

  if (LSMS.ready()!=0 && LVoiceCall.voiceCall(phoneNum))
  {
    Serial.println("Start calling...");
    // call until press button
    while(digitalRead(BTN_PIN) == HIGH) {};
    delay(1000);
    LVoiceCall.hangCall();
    Serial.println("Call Finished");
  }
  else{
    Serial.println("Call failed");
  }
  
}

