
void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.begin(115200);
  Serial.println("Serial comm via USB UART");
  Serial.flush();
}
void loop() {             
  while(Serial.available()>0){
    uint8_t byteSerial = Serial.read();
    uint8_t buff[100] = {byteSerial};
    String str (char*)buff;
    Serial.print(str);

  }      
}
