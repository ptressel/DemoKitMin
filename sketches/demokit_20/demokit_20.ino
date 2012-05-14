#include <avrpins.h>
#include <max3421e.h>
#include <usbhost.h>
#include <usb_ch9.h>
#include <Usb.h>
#include <usbhub.h>
#include <avr/pgmspace.h>
#include <address.h>

#include <adk.h>

#include <Servo.h>

#include <printhex.h>
#include <message.h>
#include <hexdump.h>
#include <parsetools.h>

// Only log high-frequency messages at this interval.
#define LOG_MSG_LOOP_INTERVAL 500
int log_msg_loop_count = 0;

USB Usb;
USBHub hub0(&Usb);
USBHub hub1(&Usb);
ADK adk(&Usb,"Google, Inc.",
            "DemoKitMin",
            "DemoKit Arduino Board",
            "1.0",
            "http://www.android.com",
            "0000000012345678");
uint8_t  b, b1;

#define LEFT_SERVO_LIMIT 86
#define RIGHT_SERVO_LIMIT 200

#define REVERSE_LIMIT 112
#define FORWARD_LIMIT 134

#define  LED1_RED       8
#define  LED1_GREEN     10
#define  LED1_BLUE      9

#define  SERVO1         11
#define  SERVO2         12
#define  SERVO3         13

#define  BUTTON1        2

Servo servos[3];

void setup();
void loop();

void init_buttons()
{
	pinMode(BUTTON1, INPUT);

	// enable the internal pullups
	digitalWrite(BUTTON1, HIGH);
}

void init_servos()
{
        servos[0].attach(SERVO1);
        servos[1].attach(SERVO2);
        servos[2].attach(SERVO3);

        Serial.println("Throttle full");
        servos[1].write(180);
        delay(6500);
        Serial.println("Throttle zero");
        servos[1].write(0);
        delay(3500);
        Serial.println("Throttle home");
        servos[1].write(90);
        delay(4000);
}

void init_leds()
{
	digitalWrite(LED1_RED, 0);

	pinMode(LED1_RED, OUTPUT);
}

void setup()
{
	Serial.begin(115200);
	Serial.println("\r\nADK demo start");
        
        if (Usb.Init() == -1) {
          Serial.println("OSCOKIRQ failed to assert");
        while(1); //halt
        }//if (Usb.Init() == -1...


	init_leds();
	init_buttons();
        init_servos();
	b1 = digitalRead(BUTTON1);

}

void loop()
{
  uint8_t rcode;
  uint8_t msg[3] = { 0x00 };
   Usb.Task();
   
   if( adk.isReady() == false ) {
	analogWrite(LED1_RED, 255);
	analogWrite(LED1_GREEN, 255);
	analogWrite(LED1_BLUE, 255);

	servos[0].write(90);
	servos[1].write(90);
	servos[2].write(90);

     return;
   }
   uint16_t len = sizeof(msg);
   
   rcode = adk.RcvData(&len, msg);
   if( rcode && ((log_msg_loop_count % LOG_MSG_LOOP_INTERVAL) == 0)) {
     USBTRACE2("Data rcv status:", rcode );
   } 
   if(len > 0) {
     USBTRACE("\r\nData Packet.");
     USBTRACE(msg[1]);
    // assumes only one command per packet
    if (msg[0] == 0x2) {
      switch( msg[1] ) {
        case 0:
          analogWrite(LED1_RED, 255 - msg[2]);
          break;
        case 1:
          analogWrite(LED1_GREEN, 255 - msg[2]);
          break;
        case 2:
          analogWrite(LED1_BLUE, 255 - msg[2]);
          break;
        case 16:
          if(msg[2] < LEFT_SERVO_LIMIT) msg[2] = LEFT_SERVO_LIMIT;
          if(msg[2] > RIGHT_SERVO_LIMIT) msg[2] = RIGHT_SERVO_LIMIT;
          Serial.print("Steering = ");
          Serial.println(msg[2]);
	  servos[0].write(map(msg[2], 0, 255, 0, 180));
          break;
        case 17:
          if(msg[2] < REVERSE_LIMIT) msg[2] = REVERSE_LIMIT;
          if(msg[2] > FORWARD_LIMIT) msg[2] = FORWARD_LIMIT;
          Serial.print("Drive = ");
          Serial.println(msg[2]);
          servos[1].write(map(msg[2], 0, 255, 0, 180));
          break;
      }//switch( msg[1]...  
    }//if (msg[0] == 0x2...
   }//if( len > 0...
   
   msg[0] = 0x1;

   b = digitalRead(BUTTON1);
   if (b != b1 || ((log_msg_loop_count % LOG_MSG_LOOP_INTERVAL) == 0)) {
     if (b != b1) {
       USBTRACE("\r\nButton state changed");
     } else {
       USBTRACE("\r\nButton state unchanged");
     }
     msg[1] = 0;
     msg[2] = b ? 0 : 1;
     rcode = adk.SndData( 3, msg );
     if( rcode ) {
       USBTRACE2("\r\nButton send: ", rcode );
     }
     b1 = b;
    }//if (b != b1...


      delay( 10 );       
}
