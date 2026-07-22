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
          sonar=Custom('sonar','./qakicons/symActorWithobjSmall.png')
          cargorobot=Custom('cargorobot','./qakicons/symActorWithobjSmall.png')
          simsonar=Custom('simsonar','./qakicons/symActorWithobjSmall.png')
          simled=Custom('simled','./qakicons/symActorWithobjSmall.png')
     with Cluster('ctxrobotsmart', graph_attr=nodeattr):
          robotsmart=Custom('robotsmart(ext)','./qakicons/externalQActor.png')
     sys >> Edge( label='container_detected', **evattr, decorate='true', fontcolor='darkgreen') >> cargoservice
     sys >> Edge( label='out_of_service', **evattr, decorate='true', fontcolor='darkgreen') >> cargoservice
     sonar >> Edge( label='container_detected', **eventedgeattr, decorate='true', fontcolor='red') >> sys
     sonar >> Edge( label='out_of_service', **eventedgeattr, decorate='true', fontcolor='red') >> sys
     cargorobot >> Edge(color='magenta', style='solid', decorate='true', label='<moverobot<font color="darkgreen"> moverobotdone moverobotfailed</font> &nbsp; >',  fontcolor='magenta') >> robotsmart
     cargoservice >> Edge(color='magenta', style='solid', decorate='true', label='<reachTarget<font color="darkgreen"> targetReached targetUnreachable</font> &nbsp; >',  fontcolor='magenta') >> cargorobot
     cargoservice >> Edge(color='blue', style='solid',  decorate='true', label='<start_detection &nbsp; stop_detection &nbsp; >',  fontcolor='blue') >> sonar
diag
