# Alwaysdata MySQL Deployment Reference Guide

This document serves as an immutable reference guide outlining the setup, structure, and exact deployment parameters utilized for the database tier of the application. The system infrastructure is split to optimize resource limits entirely within a secure cloud environment for free.

---

## 1. Cloud Architecture Overview

The full-stack application profile relies on three decoupled hosting layers to maximize performance while remaining 100% free:

* **Database Tier:** MySQL (MariaDB Engine) hosted entirely via **Alwaysdata Cloud Infrastructure** (Free Tier).
* **Backend Tier:** Spring Boot REST API targeted for deployment on **Render** (Free Web Service).
* **Frontend Tier:** Angular single-page application compiled and hosted via **Vercel** or **Netlify**.

---

## 2. Alwaysdata Database Configuration Details

The live instance parameters listed below must be directly injected into the Spring Boot environmental runtime attributes. The Alwaysdata Free Account includes a persistent **1 GB SSD storage quota** and **256 MB RAM allocation** that stays live indefinitely without expiration.

| Parameter | Active Configuration Value |
| :--- | :--- |
| **Database Type** | MariaDB / MySQL (Version 11.4 Engine) |
| **Host / Server Name** | `mysql-cinebook.alwaysdata.net` |
| **Port** | `3306` |
| **Database Name** | `cinebook_data` |
| **Database User** | `cinebook_kiran` |
| **Database Password** | `kiran@938145` |

---

## 3. Alwaysdata Interface Navigation Workflow

When managing data components or modifying configurations inside the Alwaysdata administrative portal, use the structured paths defined below:

### A. Initializing and Modifying Database Users
1. Log in to the [Alwaysdata Customer Area](https://admin.alwaysdata.com/).
2. From the primary navigation panel on the left sidebar, navigate to **Databases** > **MySQL**.
3. To inspect or manage users, shift the view from the default tab to the middle tab labeled **USERS** (marked by the multiple-profile group icon).
4. Click the **Add a user** action block to inject credentials, map passwords, and bind database permissions.

### B. Remote Access and Connection Scopes
1. Under **User Options**, the configuration leaves **"SSL connection required"** completely **unchecked** to minimize complex keystore compilation.
2. The **"Authorized IP address"** input layer is purposely left **blank**. This signals Alwaysdata to assign a global wildcard access pattern (`%`), permitting secure context processing across local machine runtimes and external cloud servers (e.g., Render) without restrictive firewall limitations.

### C. Accessing Visual Data Management via phpMyAdmin
1. Inside the main **MySQL databases** menu panel, pinpoint the core metadata yellow summary display at the top.
2. Click the pink link labeled **phpMyAdmin**.
3. Input the credentials matching your created Database User (`cinebook_kiran`) and your private password to browse columns, alter values, or process manual SQL dumps.

---

## 4. Spring Boot Setup Code Reference

Inject the precise blocks below into your `src/main/resources/application.properties` configuration layer to preserve persistence layer mapping via the Hibernate Object-Relational Mapping (ORM) framework:

```properties
# ===================================================================
# ALWAYS DATA CLOUD MYSQL DATASOURCE CONFIGURATION
# ===================================================================
spring.datasource.url=jdbc:mysql://mysql-cinebook.alwaysdata.net:3306/cinebook_data?useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=cinebook_kiran
spring.datasource.password=kiran@938145
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# ===================================================================
# HIBERNATE AUTOMATED PERSISTENCE LIFECYCLE ENGINE SETTINGS
# ===================================================================
# Automatically updates your cloud schema to match your Java @Entity classes
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect