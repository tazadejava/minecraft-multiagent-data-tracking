#!/bin/bash

output=$(mvn package)

echo "${output}"

case $output in
  *"BUILD SUCCESS"*)
    cp C:/Users/Tonit/Documents/MIT/UROP/ActionTrackerPlugin/target/ActionTracker.jar C:/Users/Tonit/Documents/MIT/UROP/DataTrackingTestServer/plugins/ActionTracker.jar -f
	echo "SUCCESS!"
	sleep 2
	;;
  *)
    echo "ERROR!"
	read -p "Press any key to continue..."
	;;
esac