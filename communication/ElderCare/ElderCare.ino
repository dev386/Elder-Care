
const char SET_PHONE = 'a';
const char SET_WIFI_SSID = 'b';
const char SET_WIFI_PWD = 'c';

#define MAXSTR 50
char val;  // 儲存接收資料的變數
char *phoneNum, *wifiSSID, *wifiPwd;

void setup() {
  Serial.begin(9600);   // 與電腦序列埠連線
  while (!Serial);
  Serial.println("BT is ready!");
  // 設定藍牙模組的連線速率
  Serial1.begin(9600);
  phoneNum = '\0';
  wifiSSID = '\0';
  wifiPwd = '\0';
}

void loop() {
  // 若收到「序列埠監控視窗」的資料，則送到藍牙模組
  if (Serial.available()) {
    val = Serial.read();
    Serial1.println(val);
  }

  // 若收到藍牙模組的資料，則送到「序列埠監控視窗」
  if (Serial1.available()) {
    val = Serial1.read();
    Serial.println(val);
    switch (val) {
      case SET_PHONE:
        phoneNum = readStr();
        Serial.print("~~");
        Serial.println(phoneNum);
        break;
      case SET_WIFI_SSID:
        wifiSSID = readStr();
        break;
      case SET_WIFI_PWD:
        wifiPwd = readStr();
        break;
    }
  }
}

char* readStr() {
  char* rev = (char*)malloc(MAXSTR*sizeof(char));
  char* tmp = rev;
  do {
    val = Serial1.read();
    *rev = val;
    rev++;
  } while (val != '@' && Serial1.available());
  rev--;
  *rev = '\0';
  rev = tmp;
  Serial.println(rev);
  return rev;
}

