%====================================================================================
% cargoservice description   
%====================================================================================
request( load_request, load_request(none) ).
reply( load_accepted, load_accepted(SlotId) ).  %%for load_request
reply( load_retrylater, load_retrylater(HOLDSTATE) ).  %%for load_request
reply( load_refused, load_refused(none) ).  %%for load_request
event( sonar_distance, sonar_distance(D) ).
dispatch( inhibit_ioport, inhibit_ioport(none) ).
dispatch( enable_ioport, enable_ioport(none) ).
dispatch( cargoservice_goto_accept_load_request, cargoservice_goto_accept_load_request(none) ).
dispatch( cargoservice_goto_waiting, cargoservice_goto_waiting(none) ).
dispatch( cargoservice_start_trip, cargoservice_start_trip(none) ).
dispatch( cargoservice_stay_engaged, cargoservice_stay_engaged(none) ).
dispatch( cargoservice_window_expired, cargoservice_window_expired(none) ).
dispatch( sonar_next_measure, sonar_next_measure(none) ).
request( get_hold, get_hold(none) ).
reply( hold_state, hold_state(HOLDSTATE) ).  %%for get_hold
request( preset_hold, preset_hold(HOLDCONFIG) ).
reply( preset_done, preset_done(ARG) ).  %%for preset_hold
event( outOfService, outOfService(none) ).
request( reachTarget, reachTarget(TARGETX,TARGETY) ).
reply( targetReached, targetReached(ARG) ).  %%for reachTarget
reply( targetUnreachable, targetUnreachable(ARG) ).  %%for reachTarget
request( buildPlan, buildPlan(PX,PY,TX,TY) ).
reply( buildPlanDone, buildPlanDone(PLAN) ).  %%for buildPlan
request( moverobot, moverobot(TARGETX,TARGETY,STEPTIME) ).
reply( moverobotdone, moverobotdone(ARG) ).  %%for moverobot
reply( moverobotfailed, moverobotfailed(PLANDONE,PLANTODO) ).  %%for moverobot
%====================================================================================
context(ctxcargoservice, "localhost",  "TCP", "8030").
context(ctxdevices, "localhost",  "TCP", "8033").
context(ctxrobotsmart, "127.0.0.1",  "TCP", "8020").
 qactor( robotsmart, ctxrobotsmart, "external").
  qactor( cargoservice, ctxcargoservice, "it.unibo.cargoservice.Cargoservice").
 static(cargoservice).
  qactor( cargorobot, ctxcargoservice, "it.unibo.cargorobot.Cargorobot").
 static(cargorobot).
  qactor( sonar, ctxdevices, "it.unibo.sonar.Sonar").
 static(sonar).
