SELECT rental_date BETWEEN (NOW() - INTERVAL 10 MINUTE) AND NOW(), inventory_id, customer_id, return_date, staff_id
FROM rental;
