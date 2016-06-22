#include <HttpClient.h>
#include <LTask.h>
#include <LWiFi.h>
#include <LDateTime.h>
#include <LWiFiClient.h>
#define WIFI_AUTH LWIFI_WPA  // choose from LWIFI_OPEN, LWIFI_WPA, or LWIFI_WEP.

#define DEVICEID "DhEOz10B" // Input your deviceId
#define DEVICEKEY "4U0aJ1bTUQqLpjt0" // Input your deviceKey
#define SITE_URL "api.mediatek.com"

#define LED_PIN 13
#define BTN_PIN 8
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
char port[4]={0};
char connection_info[21]={0};
char ip[21]={0};             
int portnum;
int val = 0;
String tcpdata = String(DEVICEID) + "," + String(DEVICEKEY) + ",";
String upload_led;
String tcpcmd_led_on = "LED_Control,1";
String tcpcmd_led_off = "LED_Control,0";

LWiFiClient c2;
HttpClient http(c2);

// ===main===
void setup() {
//Serial
  Serial.begin(9600);   // 與電腦序列埠連線
  while (!Serial);

//I/O
  pinMode(LED_PIN, OUTPUT);
  pinMode(BTN_PIN, INPUT);
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

  digitalWrite(LED_PIN, LOW);
}

void loop() {
  listenBLE();
  if(digitalRead(BTN_PIN) == LOW){ 
    Serial.println("Button Pressed");
    //sendHeartRate();
    sendFallGPS();
    delay(1000);
  }

  if(LWiFi.status() == LWIFI_STATUS_CONNECTED)
    digitalWrite(LED_PIN, HIGH);
  else
    digitalWrite(LED_PIN, LOW);
  /*
  else{
    Serial.println("Loop end");
    delay(1000);
  }*/
}

void listenBLE(){
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

void sendHeartRate(){
  Serial.println("~~~send heartBeat~~~");
  String rate = String(random(40,150));
  String data = "HeartRate,,"+rate;
  pushDataToMCS(data);
  Serial.println("~~~send heartBeat end~~~");
  
}

void sendFallGPS(){
  Serial.println("~~~send Fall GPS");
  String gps = getGPS();
  String data = "FallAlert,,"+gps;
  pushDataToMCS(data);
  Serial.println("~~~send GPS end~~~");
}

// ******* BLE other function******
char* readStr() {
  char* rev = (char*)malloc(MAXSTR*sizeof(char));
  char* tmp = rev;
  int len=0;
  do {
    r = Serial1.read();
    len = len*10+r-'0';
    
  } while (r != '!');
  len = (len - '!'+'0')/10;
  Serial.println(len);
  int i;
  for(i=0;i<len;++i){
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
void connectAP(){
  Serial.println("Connecting to AP");
  while (0 == LWiFi.connect(wifiSSID, LWiFiLoginInfo(WIFI_AUTH, wifiPwd)))
  {
    delay(1000);
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

void getconnectInfo(){
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
      connection_info[ipcount]=c;
      if(c==',')
      separater=ipcount;
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
  for(i=0;i<separater;i++)
  {  ip[i]=connection_info[i];
  }
  int j=0;
  separater++;
  for(i=separater;i<21 && j<5;i++)
  {  port[j]=connection_info[i];
     j++;
  }
  Serial.println("The TCP Socket connection instructions:");
  Serial.print("IP: ");
  Serial.println(ip);
  Serial.print("Port: ");
  Serial.println(port);
  portnum = atoi (port);

} //getconnectInfo

void pushDataToMCS(String data)
{
  while(!c2.connect(SITE_URL, 80))
  {
    Serial.println("Re-connecting to Website");
    delay(1000);
  }
  
  //TODO: get heart rate from sensor
  
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

void getResponse(){
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
  int err = http.skipResponseHeaders();

  int bodyLen = http.contentLength();
  Serial.print("Content length is: ");
  Serial.println(bodyLen);
  Serial.println();
}

// ***** GPS *********
String getGPS(){
  return "23,120,14";
}

