%====================================================================================
% cargoservice TEST configuration - Sprint 2 (unit variant of the TestPlans)
%
% The REAL cargoservice and the REAL logic sonar are deployed together with one
% test double:
%   - cargorobot -> MockCargorobot : confirms every reachTarget (targetReached)
% The logic sonar is real: each plan injects the raw sonar_data values it needs by
% publishing on the MQTT topic, exactly as the board/sim device would.
%
% REQUIRES the Mosquitto broker running (tcp://localhost:1883): the boundary
% (load_requests/hold_state) and the device stream (sonar_data/led_data) are MQTT.
% The test client also connects over TCP (8030) for the preset_hold request/reply.
%
% Regenerate the .kt from cargoservice_sprint2.qak before running (Eclipse qak).
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
  qactor( cargoservice, ctxcargoservice, "it.unibo.cargoservice.Cargoservice").
 static(cargoservice).
  qactor( sonar, ctxcargoservice, "it.unibo.sonar.Sonar").
 static(sonar).
  qactor( cargorobot, ctxcargoservice, "it.unibo.mock.MockCargorobot").
 static(cargorobot).
