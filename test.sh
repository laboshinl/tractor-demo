#/bin/bash
sbt assembly
scp /home/laboshinl/workspace/tractor-demo/target/scala-2.11/tractor.jar ubuntu@node1:~/
ssh ubuntu@node1 bash deploy.sh /home/ubuntu/pcaps/holly.pcap
