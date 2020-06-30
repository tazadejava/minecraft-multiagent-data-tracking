#!/bin/bash

output=$(mvn package)

echo "${output}"

case $output in
  *"BUILD SUCCESS"*)
    cp /home/yoshi/Documents/GenesisUROP/ActionTrackerPlugin/target/ActionTracker.jar /home/yoshi/Documents/GenesisUROP/DataTrackingTestServer/plugins/ActionTracker.jar -f
	echo "SUCCESS!"
	sleep 2
	;;
  *)
    echo "ERROR!"
	read -p "Press any key to continue..."
	;;
esac