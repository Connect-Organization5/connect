-- Postgres 컨테이너를 처음 만들 때(볼륨이 비어 있을 때) 한 번만 실행됨
-- 서비스마다 DB 를 나눠서 다른 서비스의 테이블을 조회하지 않도록 함
CREATE DATABASE connect_user;
CREATE DATABASE connect_hub;
CREATE DATABASE connect_company;
CREATE DATABASE connect_order;
CREATE DATABASE connect_delivery;
