#!/bin/bash
docker run -it --rm --network abstratium mysql mysql -h abstratium-mysql --port 3306 -u root -psecret abstrapact -e "DELETE FROM T_demo;"
