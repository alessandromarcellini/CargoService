%====================================================================================
% cargoservice description   
%====================================================================================
request( load_request, load_request(none) ).
reply( load_accepted, load_accepted(SLOTID) ).  %%for load_request
reply( load_retrylater, load_retrylater(HOLDSTATE) ).  %%for load_request
reply( load_refused, load_refused(none) ).  %%for load_request
dispatch( start_working, start_working(SlotToFill) ).
dispatch( checkMeasurement, checkMeasurement(none) ).
event( outOfService, outOfService(none) ).
event( containerPositioned, containerPositioned(none) ).
%====================================================================================
context(ctxcargoservice, "localhost",  "TCP", "8020").
context(ctxioport, "localhost",  "TCP", "8021").
context(ctxrobot, "localhost",  "TCP", "8022").
context(ctxdevices, "localhost",  "TCP", "8023").
 qactor( cargoservice, ctxcargoservice, "it.unibo.cargoservice.Cargoservice").
 static(cargoservice).
  qactor( robotservice, ctxrobot, "it.unibo.robotservice.Robotservice").
 static(robotservice).
  qactor( cargorobot, ctxrobot, "it.unibo.cargorobot.Cargorobot").
 static(cargorobot).
  qactor( sonar, ctxdevices, "it.unibo.sonar.Sonar").
 static(sonar).
  qactor( ioport, ctxioport, "it.unibo.ioport.Ioport").
 static(ioport).
  qactor( led, ctxdevices, "it.unibo.led.Led").
 static(led).
