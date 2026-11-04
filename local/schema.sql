
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
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT,
  `category_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `penalty` (
  `penalty_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `report_id` bigint DEFAULT NULL,
  `reporter_id` bigint DEFAULT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `penalty_level_id` bigint DEFAULT NULL,
  PRIMARY KEY (`penalty_id`),
  KEY `FKbb8ac2d08g2lrgqks49jefwac` (`penalty_level_id`),
  CONSTRAINT `FKbb8ac2d08g2lrgqks49jefwac` FOREIGN KEY (`penalty_level_id`) REFERENCES `penalty_level` (`penalty_level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `penalty_level` (
  `penalty_level_id` bigint NOT NULL AUTO_INCREMENT,
  `penalty_level_days` bigint DEFAULT NULL,
  `penalty_level_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`penalty_level_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `point_history` (
  `point_id` bigint NOT NULL AUTO_INCREMENT,
  `add_cost` bigint DEFAULT NULL,
  `before_cost` bigint DEFAULT NULL,
  `cost` bigint DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `type_detail` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`point_id`),
  KEY `FK9yptgx9qhcp768sokuopwxncm` (`user_id`),
  CONSTRAINT `FK9yptgx9qhcp768sokuopwxncm` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `private_chat_message` (
  `private_chat_id` bigint NOT NULL AUTO_INCREMENT,
  `content_type` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `visible` bit(1) DEFAULT b'1',
  `private_chat_room_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`private_chat_id`),
  KEY `FKn28ns0fq8xgedoltv3km93yli` (`private_chat_room_id`),
  KEY `FKodw7xst5aa6c9xggp3t4hmdsu` (`user_id`),
  CONSTRAINT `FKn28ns0fq8xgedoltv3km93yli` FOREIGN KEY (`private_chat_room_id`) REFERENCES `private_chat_room` (`private_chat_room_id`),
  CONSTRAINT `FKodw7xst5aa6c9xggp3t4hmdsu` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `private_chat_message_read` (
  `private_chat_message_read_id` bigint NOT NULL AUTO_INCREMENT,
  `private_chat_message_id` bigint DEFAULT NULL,
  `private_chat_room_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`private_chat_message_read_id`),
  KEY `FK82d04gfgkqjahqkr2cc3nu4d0` (`private_chat_message_id`),
  KEY `FK4ocqldc5dye9gqkduao5olynp` (`private_chat_room_id`),
  KEY `FKssjd0ikdwxg21k8c817j8wq5i` (`user_id`),
  CONSTRAINT `FK4ocqldc5dye9gqkduao5olynp` FOREIGN KEY (`private_chat_room_id`) REFERENCES `private_chat_room` (`private_chat_room_id`),
  CONSTRAINT `FK82d04gfgkqjahqkr2cc3nu4d0` FOREIGN KEY (`private_chat_message_id`) REFERENCES `private_chat_message` (`private_chat_id`),
  CONSTRAINT `FKssjd0ikdwxg21k8c817j8wq5i` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `private_chat_room` (
  `private_chat_room_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `visible` bit(1) DEFAULT b'1',
  `buyer_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `seller_id` bigint DEFAULT NULL,
  PRIMARY KEY (`private_chat_room_id`),
  KEY `FK3l9jjy7siv0nerpjgeohs1ij4` (`buyer_id`),
  KEY `FK4pw53b1ff43xt46spitxwex0w` (`product_id`),
  KEY `FKbud9bevhedinhi156v9ebh72` (`seller_id`),
  CONSTRAINT `FK3l9jjy7siv0nerpjgeohs1ij4` FOREIGN KEY (`buyer_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FK4pw53b1ff43xt46spitxwex0w` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`),
  CONSTRAINT `FKbud9bevhedinhi156v9ebh72` FOREIGN KEY (`seller_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
  `product_id` bigint NOT NULL AUTO_INCREMENT,
  `content` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `discountable` bit(1) DEFAULT b'0',
  `for_sale` bit(1) DEFAULT b'1',
  `free_shipping` bit(1) DEFAULT b'0',
  `is_escrow` bit(1) DEFAULT b'0',
  `price` bigint NOT NULL,
  `purchase_at` varchar(255) DEFAULT NULL,
  `refreshed_at` datetime(6) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `visible` bit(1) DEFAULT b'1',
  `category_id` bigint NOT NULL,
  `status_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`product_id`),
  KEY `FK1mtsbur82frn64de7balymq9s` (`category_id`),
  KEY `FK8556hocjcb04st51nt8yknfbg` (`status_id`),
  KEY `FK979liw4xk18ncpl87u4tygx2u` (`user_id`),
  CONSTRAINT `FK1mtsbur82frn64de7balymq9s` FOREIGN KEY (`category_id`) REFERENCES `category` (`category_id`),
  CONSTRAINT `FK8556hocjcb04st51nt8yknfbg` FOREIGN KEY (`status_id`) REFERENCES `status` (`status_id`),
  CONSTRAINT `FK979liw4xk18ncpl87u4tygx2u` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_image` (
  `product_image_id` bigint NOT NULL AUTO_INCREMENT,
  `content_type` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `visible` bit(1) DEFAULT b'1',
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`product_image_id`),
  KEY `FK6oo0cvcdtb6qmwsga468uuukk` (`product_id`),
  CONSTRAINT `FK6oo0cvcdtb6qmwsga468uuukk` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_thumbnail` (
  `thumbnail_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `thumbnail_url` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`thumbnail_id`),
  UNIQUE KEY `UK_9omjsc6tmc38cvmsc7hr786f1` (`product_id`),
  CONSTRAINT `FK12etorkxgc82lav442ndpbjjd` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `public_chat` (
  `public_chat_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `visible` bit(1) DEFAULT b'1',
  `product_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `is_image` bit(1) DEFAULT b'0',
  PRIMARY KEY (`public_chat_id`),
  KEY `FKn3jjsiowrq70uxgvuglt0dtx7` (`product_id`),
  KEY `FKlidr1kmynsfgoyqc6h2tj2yq7` (`user_id`),
  CONSTRAINT `FKlidr1kmynsfgoyqc6h2tj2yq7` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FKn3jjsiowrq70uxgvuglt0dtx7` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report` (
  `report_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `is_report` bit(1) DEFAULT NULL,
  `private_chat_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `report_contents` varchar(255) DEFAULT NULL,
  `reporter_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `visible` bit(1) DEFAULT NULL,
  `report_type_id` bigint DEFAULT NULL,
  PRIMARY KEY (`report_id`),
  KEY `FK989tfinp1jxv79fvmju0cfpj3` (`report_type_id`),
  KEY `FKndpjl61ubcm2tkf7ml1ynq13t` (`reporter_id`),
  KEY `FKj62onw73yx1qnmd57tcaa9q3a` (`user_id`),
  KEY `FKasd7dbkinlhec2y8onuw4w8c7` (`product_id`),
  CONSTRAINT `FK989tfinp1jxv79fvmju0cfpj3` FOREIGN KEY (`report_type_id`) REFERENCES `report_type` (`report_type_id`),
  CONSTRAINT `FKasd7dbkinlhec2y8onuw4w8c7` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`),
  CONSTRAINT `FKj62onw73yx1qnmd57tcaa9q3a` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FKndpjl61ubcm2tkf7ml1ynq13t` FOREIGN KEY (`reporter_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_image` (
  `report_image_id` bigint NOT NULL AUTO_INCREMENT,
  `content_type` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `report_id` bigint DEFAULT NULL,
  PRIMARY KEY (`report_image_id`),
  KEY `FK665qkgam1hh2blo5v9vgj8txf` (`report_id`),
  CONSTRAINT `FK665qkgam1hh2blo5v9vgj8txf` FOREIGN KEY (`report_id`) REFERENCES `report` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_type` (
  `report_type_id` bigint NOT NULL AUTO_INCREMENT,
  `report_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`report_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `status` (
  `status_id` bigint NOT NULL AUTO_INCREMENT,
  `status_name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`status_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tag` (
  `tag_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `tag_name` varchar(255) DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`tag_id`),
  KEY `FKdal0uofqrt3xxhyig2jcd0dmi` (`product_id`),
  CONSTRAINT `FKdal0uofqrt3xxhyig2jcd0dmi` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transaction_details` (
  `transaction_details_id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `price` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `consumer_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `seller_id` bigint DEFAULT NULL,
  `status_id` bigint DEFAULT NULL,
  PRIMARY KEY (`transaction_details_id`),
  UNIQUE KEY `UK_jyw02qqdfpapavnochusslnq6` (`product_id`),
  KEY `FKiw0yo5wnu3ys3ur52sri93jmy` (`consumer_id`),
  KEY `FKkeci36n9oux10ltq5ps66v305` (`seller_id`),
  KEY `FKfig2t9fldpbqope59l5taeswn` (`status_id`),
  CONSTRAINT `FKfig2t9fldpbqope59l5taeswn` FOREIGN KEY (`status_id`) REFERENCES `transaction_status` (`status_id`),
  CONSTRAINT `FKiw0yo5wnu3ys3ur52sri93jmy` FOREIGN KEY (`consumer_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FKjleopb277j0ax03h7fcial78b` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`),
  CONSTRAINT `FKkeci36n9oux10ltq5ps66v305` FOREIGN KEY (`seller_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transaction_request` (
  `transaction_request_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `is_accepted` bit(1) DEFAULT NULL,
  `price` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `buyer_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  PRIMARY KEY (`transaction_request_id`),
  KEY `FKe8qu0f7odcjtfi7ctogf6obxx` (`buyer_id`),
  KEY `FKqfyr0cii6pp1hg65g0eelyg0m` (`product_id`),
  KEY `FKoe7bt5n933shkv2unsnpc0pxn` (`seller_id`),
  CONSTRAINT `FKe8qu0f7odcjtfi7ctogf6obxx` FOREIGN KEY (`buyer_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FKoe7bt5n933shkv2unsnpc0pxn` FOREIGN KEY (`seller_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `FKqfyr0cii6pp1hg65g0eelyg0m` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transaction_status` (
  `status_id` bigint NOT NULL AUTO_INCREMENT,
  `status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`status_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `account_bank` varchar(255) DEFAULT NULL,
  `account_number` varchar(255) DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `nickname` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `phone_number` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `visible` bit(1) DEFAULT b'1',
  `role_id` bigint DEFAULT NULL,
  `channel` varchar(255) DEFAULT 'LOCAL',
  PRIMARY KEY (`user_id`),
  KEY `FKn4pb12f3y8ktduy8fnc2xlln1` (`role_id`),
  CONSTRAINT `FKn4pb12f3y8ktduy8fnc2xlln1` FOREIGN KEY (`role_id`) REFERENCES `user_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_role` (
  `role_id` bigint NOT NULL AUTO_INCREMENT,
  `role_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

