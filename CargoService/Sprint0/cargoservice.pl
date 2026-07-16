%====================================================================================
% cargoservice description   
%====================================================================================
request( load_request, load_request(none) ).
reply( load_accepted, load_accepted(SLOTID) ).  %%for load_request
reply( retrylater, retrylater(HOLDSTATE) ).  %%for load_request
reply( load_refused, load_refused(none) ).  %%for load_request
%====================================================================================
context(ctxmock, "localhost",  "TCP", "8020").
 qactor( cargoservice, ctxmock, "it.unibo.cargoservice.Cargoservice").
 static(cargoservice).
  qactor( robotservice, ctxmock, "it.unibo.robotservice.Robotservice").
 static(robotservice).
  qactor( cargorobot, ctxmock, "it.unibo.cargorobot.Cargorobot").
 static(cargorobot).
  qactor( sonar, ctxmock, "it.unibo.sonar.Sonar").
 static(sonar).
  qactor( ioport, ctxmock, "it.unibo.ioport.Ioport").
 static(ioport).
  qactor( led, ctxmock, "it.unibo.led.Led").
 static(led).
