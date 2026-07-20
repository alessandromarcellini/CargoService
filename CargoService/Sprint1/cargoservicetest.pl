%====================================================================================
% cargoservice TEST configuration (unit variant of the TestPlans, sprint1_v1.html)
%
% The real cargoservice is deployed together with two test doubles:
%   - cargorobot -> MockCargorobot : confirms every reachTarget (targetReached)
%   - ioport     -> MockIoport    : absorbs inhibit_ioport/enable_ioport
% No sonar is deployed: the sonar is proactive in production, and in the tests
% each plan injects the sonar_distance(D) event it needs (deterministic Given).
% All the actors are co-located in ctxcargoservice: thanks to the location
% transparency of qak the service code is exactly the production one, only this
% deployment description changes. The test client plays the customer boundary
% over TCP (port 8030).
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
%====================================================================================
context(ctxcargoservice, "localhost",  "TCP", "8030").
  qactor( cargoservice, ctxcargoservice, "it.unibo.cargoservice.Cargoservice").
 static(cargoservice).
  qactor( cargorobot, ctxcargoservice, "it.unibo.mock.MockCargorobot").
 static(cargorobot).
  qactor( ioport, ctxcargoservice, "it.unibo.mock.MockIoport").
 static(ioport).
