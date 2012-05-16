#include <avrpins.h>
#include <max3421e.h>
#include <usbhost.h>
#include <usb_ch9.h>
#include <Usb.h>
#include <usbhub.h>
#include <avr/pgmspace.h>
#include <address.h>

#include <adk.h>

#include <printhex.h>
#include <message.h>
#include <hexdump.h>
#include <parsetools.h>

// Only log high-frequency messages at this interval.
#define LOG_MSG_LOOP_INTERVAL 10000
int log_msg_loop_count = 0;
// Normal return codes from RcvData -- anything else is an error:
#define SUCCESS 0x00
#define NAK 0x04  // no data available
// Heartbeat message
char* WHERE_ARE_YOU = "Where are you?\r\n";

USB Usb;
USBHub hub0(&Usb);
USBHub hub1(&Usb);
ADK adk(&Usb,"Google, Inc.",
            "DemoKitMin",
            "DemoKit Arduino Board",
            "1.0",
            "http://www.android.com",
            "0000000012345678");

void setup()
{
	Serial.begin(115200);
	Serial.println("\r\nADK demo start");
        
        if (Usb.Init() == -1) {
          Serial.println("OSCOKIRQ failed to assert");
        while(1); //halt
        }//if (Usb.Init() == -1...
}

void loop()
{
   uint8_t rcode;
   uint8_t msg[100] = {0};
   uint8_t* ptr;
   Usb.Task();
   
   if( adk.isReady() == false ) {
     USBTRACE("\r\nisReady is false.");
     delay(1000);
     return;
   }
   uint16_t len = sizeof(msg);
   
   rcode = adk.RcvData(&len, msg);
   if (rcode != SUCCESS) {
     if (rcode != NAK) {
       USBTRACE2("\r\nRcvData status:", rcode );
     } else if ((log_msg_loop_count % LOG_MSG_LOOP_INTERVAL) == 0) {
       USBTRACE("\r\nNo data");
     }
   } else {
     if(len > 0) {
       USBTRACE2("\r\nData length is ", len);
       USBTRACE((char *)msg);
       // assumes only one message per packet
       rcode = adk.SndData( len, msg );
       if (rcode) {
         USBTRACE2("\r\nSndData status: ", rcode );
       }
     } else {
       USBTRACE("\r\nData length is 0");
     }
   }
   
   // Send a heartbeat message on same schedule as the "no data" log msg.
   if ((log_msg_loop_count % LOG_MSG_LOOP_INTERVAL) == 0) {
     ptr = (uint8_t*)WHERE_ARE_YOU;
     len = strlen(WHERE_ARE_YOU);
     USBTRACE("\r\nSending heartbeat");
     rcode = adk.SndData(len, ptr);
   }
   
   delay( 10 );       
}
