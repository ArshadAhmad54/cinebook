-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: localhost    Database: cinebook
-- ------------------------------------------------------
-- Server version	8.0.46

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `cinebook`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `cinebook` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `cinebook`;

--
-- Table structure for table `admins`
--

DROP TABLE IF EXISTS `admins`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admins` (
  `admin_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`admin_id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `booking_seats`
--

DROP TABLE IF EXISTS `booking_seats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `booking_seats` (
  `booking_id` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `show_id` int NOT NULL,
  `seat_id` int NOT NULL,
  `price` decimal(8,2) NOT NULL,
  PRIMARY KEY (`booking_id`,`seat_id`),
  UNIQUE KEY `uq_show_seat` (`show_id`,`seat_id`),
  KEY `seat_id` (`seat_id`),
  CONSTRAINT `booking_seats_ibfk_1` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`booking_id`) ON DELETE CASCADE,
  CONSTRAINT `booking_seats_ibfk_2` FOREIGN KEY (`show_id`) REFERENCES `shows` (`show_id`),
  CONSTRAINT `booking_seats_ibfk_3` FOREIGN KEY (`seat_id`) REFERENCES `seats` (`seat_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `bookings`
--

DROP TABLE IF EXISTS `bookings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bookings` (
  `booking_id` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_id` int NOT NULL,
  `show_id` int NOT NULL,
  `booked_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `total_amount` decimal(10,2) NOT NULL DEFAULT '0.00',
  `status` enum('Confirmed','Cancelled') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Confirmed',
  PRIMARY KEY (`booking_id`),
  KEY `show_id` (`show_id`),
  KEY `idx_bookings_cust` (`customer_id`),
  CONSTRAINT `bookings_ibfk_1` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`customer_id`),
  CONSTRAINT `bookings_ibfk_2` FOREIGN KEY (`show_id`) REFERENCES `shows` (`show_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `customers`
--

DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `customer_id` int NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(15) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`customer_id`),
  UNIQUE KEY `email` (`email`),
  CONSTRAINT `chk_email` CHECK ((`email` like _utf8mb4'%_@_%._%'))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `movies`
--

DROP TABLE IF EXISTS `movies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `movies` (
  `movie_id` int NOT NULL AUTO_INCREMENT,
  `title` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `genre` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `duration_min` smallint NOT NULL,
  `language` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  PRIMARY KEY (`movie_id`),
  CONSTRAINT `movies_chk_1` CHECK ((`duration_min` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payments` (
  `payment_id` int NOT NULL AUTO_INCREMENT,
  `booking_id` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `method` enum('UPI','Card','NetBanking','Cash') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('Paid','Refunded') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Paid',
  `paid_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`payment_id`),
  KEY `booking_id` (`booking_id`),
  CONSTRAINT `payments_ibfk_1` FOREIGN KEY (`booking_id`) REFERENCES `bookings` (`booking_id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `screens`
--

DROP TABLE IF EXISTS `screens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `screens` (
  `screen_id` int NOT NULL AUTO_INCREMENT,
  `screen_name` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_rows` tinyint NOT NULL,
  `seats_per_row` tinyint NOT NULL,
  PRIMARY KEY (`screen_id`),
  UNIQUE KEY `screen_name` (`screen_name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `seats`
--

DROP TABLE IF EXISTS `seats`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `seats` (
  `seat_id` int NOT NULL AUTO_INCREMENT,
  `screen_id` int NOT NULL,
  `row_label` char(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  `seat_number` tinyint NOT NULL,
  `seat_type` enum('Standard','Premium') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'Standard',
  PRIMARY KEY (`seat_id`),
  UNIQUE KEY `uq_seat` (`screen_id`,`row_label`,`seat_number`),
  CONSTRAINT `seats_ibfk_1` FOREIGN KEY (`screen_id`) REFERENCES `screens` (`screen_id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=161 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `shows`
--

DROP TABLE IF EXISTS `shows`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `shows` (
  `show_id` int NOT NULL AUTO_INCREMENT,
  `movie_id` int NOT NULL,
  `screen_id` int NOT NULL,
  `show_date` date NOT NULL,
  `show_time` time NOT NULL,
  `base_price` decimal(8,2) NOT NULL,
  PRIMARY KEY (`show_id`),
  UNIQUE KEY `uq_show_slot` (`screen_id`,`show_date`,`show_time`),
  KEY `movie_id` (`movie_id`),
  KEY `idx_shows_date` (`show_date`),
  CONSTRAINT `shows_ibfk_1` FOREIGN KEY (`movie_id`) REFERENCES `movies` (`movie_id`),
  CONSTRAINT `shows_ibfk_2` FOREIGN KEY (`screen_id`) REFERENCES `screens` (`screen_id`),
  CONSTRAINT `shows_chk_1` CHECK ((`base_price` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Temporary view structure for view `v_booking_details`
--

DROP TABLE IF EXISTS `v_booking_details`;
/*!50001 DROP VIEW IF EXISTS `v_booking_details`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_booking_details` AS SELECT 
 1 AS `booking_id`,
 1 AS `full_name`,
 1 AS `email`,
 1 AS `title`,
 1 AS `show_date`,
 1 AS `show_time`,
 1 AS `screen_name`,
 1 AS `seats`,
 1 AS `total_amount`,
 1 AS `status`*/;
SET character_set_client = @saved_cs_client;

--
-- Temporary view structure for view `v_show_availability`
--

DROP TABLE IF EXISTS `v_show_availability`;
/*!50001 DROP VIEW IF EXISTS `v_show_availability`*/;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `v_show_availability` AS SELECT 
 1 AS `show_id`,
 1 AS `title`,
 1 AS `screen_name`,
 1 AS `show_date`,
 1 AS `show_time`,
 1 AS `base_price`,
 1 AS `total_seats`,
 1 AS `seats_booked`,
 1 AS `seats_free`*/;
SET character_set_client = @saved_cs_client;

--
-- Current Database: `cinebook`
--

USE `cinebook`;

--
-- Final view structure for view `v_booking_details`
--

/*!50001 DROP VIEW IF EXISTS `v_booking_details`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `v_booking_details` AS select `b`.`booking_id` AS `booking_id`,`c`.`full_name` AS `full_name`,`c`.`email` AS `email`,`m`.`title` AS `title`,`sh`.`show_date` AS `show_date`,`sh`.`show_time` AS `show_time`,`sc`.`screen_name` AS `screen_name`,group_concat(concat(`st`.`row_label`,`st`.`seat_number`) order by `st`.`row_label` ASC,`st`.`seat_number` ASC separator ',') AS `seats`,`b`.`total_amount` AS `total_amount`,`b`.`status` AS `status` from ((((((`bookings` `b` join `customers` `c` on((`c`.`customer_id` = `b`.`customer_id`))) join `shows` `sh` on((`sh`.`show_id` = `b`.`show_id`))) join `movies` `m` on((`m`.`movie_id` = `sh`.`movie_id`))) join `screens` `sc` on((`sc`.`screen_id` = `sh`.`screen_id`))) left join `booking_seats` `bs` on((`bs`.`booking_id` = `b`.`booking_id`))) left join `seats` `st` on((`st`.`seat_id` = `bs`.`seat_id`))) group by `b`.`booking_id`,`c`.`full_name`,`c`.`email`,`m`.`title`,`sh`.`show_date`,`sh`.`show_time`,`sc`.`screen_name`,`b`.`total_amount`,`b`.`status` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;

--
-- Final view structure for view `v_show_availability`
--

/*!50001 DROP VIEW IF EXISTS `v_show_availability`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */
/*!50001 VIEW `v_show_availability` AS select `sh`.`show_id` AS `show_id`,`m`.`title` AS `title`,`sc`.`screen_name` AS `screen_name`,`sh`.`show_date` AS `show_date`,`sh`.`show_time` AS `show_time`,`sh`.`base_price` AS `base_price`,(`sc`.`total_rows` * `sc`.`seats_per_row`) AS `total_seats`,count(`bs`.`seat_id`) AS `seats_booked`,((`sc`.`total_rows` * `sc`.`seats_per_row`) - count(`bs`.`seat_id`)) AS `seats_free` from (((`shows` `sh` join `movies` `m` on((`m`.`movie_id` = `sh`.`movie_id`))) join `screens` `sc` on((`sc`.`screen_id` = `sh`.`screen_id`))) left join `booking_seats` `bs` on((`bs`.`show_id` = `sh`.`show_id`))) group by `sh`.`show_id`,`m`.`title`,`sc`.`screen_name`,`sh`.`show_date`,`sh`.`show_time`,`sh`.`base_price`,`sc`.`total_rows`,`sc`.`seats_per_row` */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-11-17  0:44:11
