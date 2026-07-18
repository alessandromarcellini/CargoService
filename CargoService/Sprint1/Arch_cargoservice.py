### conda install diagrams
from diagrams import Cluster, Diagram, Edge
from diagrams.custom import Custom
import os
os.environ['PATH'] += os.pathsep + 'C:/Program Files/Graphviz/bin/'

graphattr = {     #https://www.graphviz.org/doc/info/attrs.html
    'fontsize': '22',
}

nodeattr = {   
    'fontsize': '22',
    'bgcolor': 'lightyellow'
}

eventedgeattr = {
    'color': 'red',
    'style': 'dotted'
}
evattr = {
    'color': 'darkgreen',
    'style': 'dotted'
}
with Diagram('cargoserviceArch', show=False, outformat='png', graph_attr=graphattr) as diag:
  with Cluster('env'):
     sys = Custom('','./qakicons/system.png')
### see https://renenyffenegger.ch/notes/tools/Graphviz/attributes/label/HTML-like/index
     with Cluster('ctxcargoservice', graph_attr=nodeattr):
          cargoservice=Custom('cargoservice','./qakicons/symActorWithobjSmall.png')
          cargorobot=Custom('cargorobot','./qakicons/symActorWithobjSmall.png')
     with Cluster('ctxioport', graph_attr=nodeattr):
          ioport=Custom('ioport','./qakicons/symActorWithobjSmall.png')
     with Cluster('ctxrobotsmart', graph_attr=nodeattr):
          robotsmart=Custom('robotsmart(ext)','./qakicons/externalQActor.png')
     with Cluster('ctxdevices', graph_attr=nodeattr):
          sonar=Custom('sonar','./qakicons/symActorWithobjSmall.png')
     sys >> Edge( label='containerPositioned', **evattr, decorate='true', fontcolor='darkgreen') >> cargoservice
     sys >> Edge( label='outOfService', **evattr, decorate='true', fontcolor='darkgreen') >> cargoservice
     sonar >> Edge( label='outOfService', **eventedgeattr, decorate='true', fontcolor='red') >> sys
     sonar >> Edge( label='containerPositioned', **eventedgeattr, decorate='true', fontcolor='red') >> sys
     ioport >> Edge(color='magenta', style='solid', decorate='true', label='<load_request<font color="darkgreen"> load_accepted load_retrylater load_refused</font> &nbsp; >',  fontcolor='magenta') >> cargoservice
     cargorobot >> Edge(color='magenta', style='solid', decorate='true', label='<moverobot<font color="darkgreen"> moverobotdone moverobotfailed</font> &nbsp; >',  fontcolor='magenta') >> robotsmart
     cargoservice >> Edge(color='magenta', style='solid', decorate='true', label='<reachTarget<font color="darkgreen"> targetReached targetUnreachable</font> &nbsp; >',  fontcolor='magenta') >> cargorobot
     cargoservice >> Edge(color='blue', style='solid',  decorate='true', label='<checkMeasurement &nbsp; >',  fontcolor='blue') >> sonar
diag
