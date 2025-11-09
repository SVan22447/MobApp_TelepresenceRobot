#include <ArduinoJson.h>
void setup() {
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.begin(115200);
  Serial.println("Serial comm via USB UART");
  Serial.flush();
}
void loop() {             
  if(Serial.available()){
    StaticJsonDocument<400> doc;
    DeserializationError err= deserializeJson(doc, Serial);
    if(err==DeserializationError::Ok){
      const char* direction = doc["direction"];
      bool action = doc["action"];
      Serial.print("direction = ");
      Serial.println(direction);
      Serial.print("action = ");
      Serial.println(action);
      ledChange(action);
    }else{
      Serial.print("deserializeJson() returned ");
      Serial.println(err.c_str());
      while(Serial.available()>0){
        Serial.read();
      }
    }
  }      
}
void ledChange(bool Check){
  if(Check){
    digitalWrite(LED_BUILTIN, LOW);
  }else{
    digitalWrite(LED_BUILTIN, HIGH);
  }
}
