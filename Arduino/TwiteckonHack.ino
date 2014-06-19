#include <Servo.h> 

Servo servo0; //direita
Servo servo2; //esquerda
int led = 3;
void setup() { 
  Serial.begin(9600);
  servo0.attach(8);
  servo2.attach(10);
  pinMode(led, OUTPUT);     
  digitalWrite(led, HIGH);
  setStartPos();
} 

void loop() {
  if (Serial.available() > 0) {
    Serial.print("oirs");
    int value = intParse(1);
    if(value == 1){
      dance();
    }
    if(value == 2){
      notification();
    }
  }
  delay(20);
} 

int intParse(byte length){
  //return parsed byte from serial    
  char BUFFER[length + 1];
  delay(5); //need for Baud 9600, less delay for faster speed
  for(byte i = 0; i < length; i++)    
    BUFFER[i] = Serial.read();   
  BUFFER[length] = 0; //I had this earlier, but not necessary for Arduino.
  return atoi(BUFFER);
}


void setStartPos(){
  servo0.write (0);
  servo2.write (180);
}

void dance(){
  int angleIncrement = 1;
  int incrementDelay = 2;
  for (int times = 0; times < 5; times ++) {
    for (int angle = 0; angle < 180; angle += angleIncrement) { 
      servo0.write (angle);
      servo2.write (angle);
      delay (incrementDelay);
    }
  }
  incrementDelay = 10;
  for (int times = 0; times < 3; times ++) {
    for (int angle = 0; angle < 180; angle += angleIncrement) { 
      servo0.write (angle);
      servo2.write ((angle-180)*-1);
      delay (incrementDelay); 
    }
  }
  setStartPos();
}
void notification(){
  servo2.write(20);
  for( int times = 0; times < 5; times++){
    digitalWrite(led, HIGH);
    delay(100);
    digitalWrite(led, LOW);
    delay(100);
  }
  digitalWrite(led, HIGH);
  
  servo2.write(180);
}
