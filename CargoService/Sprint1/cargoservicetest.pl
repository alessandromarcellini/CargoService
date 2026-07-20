%====================================================================================
% cargoservice TEST configuration (unit variant of the TestPlans, sprint1_v0.html)
%
% The real cargoservice is deployed together with two test doubles:
%   - cargorobot -> MockCargorobot : confirms every reachTarget (targetReached)
%   - sonar      -> MockSonar      : absorbs checkMeasurement, never emits
% All the actors are co-located in ctxcargoservice: thanks to the location
% transparency of qak the service code is exactly the production one, only this
% deployment description changes. The test client plays the ioport role via TCP
% (port 8030) and injects the containerPositioned event when a TestPlan needs it.
%====================================================================================
request( load_request, load_request(none) ).
reply( load_accepted, load_accepted(SlotId) ).  %%for load_request
reply( load_retrylater, load_retrylater(HOLDSTATE) ).  %%for load_request
reply( load_refused, load_refused(none) ).  %%for load_request
dispatch( checkMeasurement, checkMeasurement(none) ).
dispatch( cargoservice_goto_accept_load_request, cargoservice_goto_accept_load_request(none) ).
dispatch( cargoservice_goto_waiting, cargoservice_goto_waiting(none) ).
dispatch( sonar_measure_done, sonar_measure_done(none) ).
dispatch( sonar_measure_again, sonar_measure_again(none) ).
request( get_hold, get_hold(none) ).
reply( hold_state, hold_state(HOLDSTATE) ).  %%for get_hold
request( preset_hold, preset_hold(HOLDCONFIG) ).
reply( preset_done, preset_done(ARG) ).  %%for preset_hold
event( outOfService, outOfService(none) ).
event( containerPositioned, containerPositioned(none) ).
request( reachTarget, reachTarget(TARGETX,TARGETY) ).
reply( targetReached, targetReached(ARG) ).  %%for reachTarget
reply( targetUnreachable, targetUnreachable(ARG) ).  %%for reachTarget
%====================================================================================
context(ctxcargoservice, "localhost",  "TCP", "8030").
  qactor( cargoservice, ctxcargoservice, "it.unibo.cargoservice.Cargoservice").
 static(cargoservice).
  qactor( cargorobot, ctxcargoservice, "it.unibo.mock.MockCargorobot").
 static(cargorobot).
  qactor( sonar, ctxcargoservice, "it.unibo.mock.MockSonar").
 static(sonar).
