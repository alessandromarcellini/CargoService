%====================================================================================
% cargoservice description   
%====================================================================================
mqttBroker("localhost", "1883", "cargoservice").
dispatch( load_request, load_request(none) ).
event( holdstatus, holdstatus(STATE,WORKING,S1,S2,S3,S4,RESERVED) ).
event( container_detected, container_detected(none) ).
event( out_of_service, out_of_service(none) ).
dispatch( start_detection, start_detection(none) ).
dispatch( stop_detection, stop_detection(none) ).
dispatch( cargoservice_goto_accept, cargoservice_goto_accept(none) ).
dispatch( cargoservice_goto_waiting, cargoservice_goto_waiting(none) ).
dispatch( cargoservice_window_expired, cargoservice_window_expired(none) ).
dispatch( sonar_tick, sonar_tick(none) ).
request( preset_hold, preset_hold(HOLDCONFIG) ).
reply( preset_done, preset_done(ARG) ).  %%for preset_hold
request( reachTarget, reachTarget(TARGETX,TARGETY) ).
reply( targetReached, targetReached(ARG) ).  %%for reachTarget
reply( targetUnreachable, targetUnreachable(ARG) ).  %%for reachTarget
request( moverobot, moverobot(TARGETX,TARGETY,STEPTIME) ).
reply( moverobotdone, moverobotdone(ARG) ).  %%for moverobot
reply( moverobotfailed, moverobotfailed(PLANDONE,PLANTODO) ).  %%for moverobot
%====================================================================================
context(ctxcargoservice, "localhost",  "TCP", "8030").
context(ctxrobotsmart, "127.0.0.1",  "TCP", "8020").
 qactor( robotsmart, ctxrobotsmart, "external").
  qactor( cargoservice, ctxcargoservice, "it.unibo.cargoservice.Cargoservice").
 static(cargoservice).
  qactor( sonar, ctxcargoservice, "it.unibo.sonar.Sonar").
 static(sonar).
  qactor( cargorobot, ctxcargoservice, "it.unibo.cargorobot.Cargorobot").
 static(cargorobot).
