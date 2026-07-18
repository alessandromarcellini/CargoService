%====================================================================================
% cargoservice description   
%====================================================================================
request( load_request, load_request(none) ).
reply( load_accepted, load_accepted(SlotId) ).  %%for load_request
reply( load_retrylater, load_retrylater(HOLDSTATE) ).  %%for load_request
reply( load_refused, load_refused(none) ).  %%for load_request
dispatch( checkMeasurement, checkMeasurement(none) ).
dispatch( cargoservice_goto_accept_load_request, cargoservice_goto_accept_load_request(none) ).
dispatch( cargoservice_goto_waiting, cargoservice_goto_waiting(none) ).
event( outOfService, outOfService(none) ).
event( containerPositioned, containerPositioned(none) ).
request( reachTarget, reachTarget(TARGETX,TARGETY) ).
reply( targetReached, targetReached(ARG) ).  %%for reachTarget
reply( targetUnreachable, targetUnreachable(ARG) ).  %%for reachTarget
request( buildPlan, buildPlan(PX,PY,TX,TY) ).
reply( buildPlanDone, buildPlanDone(PLAN) ).  %%for buildPlan
request( moverobot, moverobot(TARGETX,TARGETY,STEPTIME) ).
reply( moverobotdone, moverobotdone(ARG) ).  %%for moverobot
reply( moverobotfailed, moverobotfailed(PLANDONE,PLANTODO) ).  %%for moverobot
%====================================================================================
context(ctxcargoservice, "localhost",  "TCP", "8019").
context(ctxioport, "localhost",  "TCP", "8021").
context(ctxrobotsmart, "127.0.0.1",  "TCP", "8020").
context(ctxdevices, "localhost",  "TCP", "8023").
 qactor( robotsmart, ctxrobotsmart, "external").
  qactor( cargoservice, ctxcargoservice, "it.unibo.cargoservice.Cargoservice").
 static(cargoservice).
  qactor( cargorobot, ctxcargoservice, "it.unibo.cargorobot.Cargorobot").
 static(cargorobot).
  qactor( sonar, ctxdevices, "it.unibo.sonar.Sonar").
 static(sonar).
  qactor( ioport, ctxioport, "it.unibo.ioport.Ioport").
 static(ioport).
