const express = require('express');
const { Pool } = require('pg');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

// Cadena de conexión con la contraseña codificada para evitar errores de sintaxis
const connectionString = 'postgresql://postgres.obybzhqtluthaqyrrxpj:9EmfQ7UpeuQ7%21%26P@aws-0-sa-east-1.pooler.supabase.com:5432/postgres';

const pool = new Pool({
    connectionString: connectionString,
    ssl: { rejectUnauthorized: false }
});

// Endpoint GET: Obtener todos los recursos desde la nube
app.get('/api/recursos', async (req, res) => {
    try {
        const resultado = await pool.query('SELECT * FROM recursos_demo ORDER BY id ASC');
        res.json(resultado.rows);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: error.message });
    }
});

// Endpoint POST: Registrar un nuevo recurso en la nube
app.post('/api/recursos', async (req, res) => {
    const { titulo, tipo } = req.body;
    try {
        const query = 'INSERT INTO recursos_demo (titulo, tipo) VALUES ($1, $2) RETURNING *';
        const nuevo = await pool.query(query, [titulo, tipo]);
        res.json(nuevo.rows[0]);
    } catch (error) {
        console.error(error);
        res.status(500).json({ error: error.message });
    }
});

const PORT = 3000;
app.listen(PORT, () => {
    console.log(`Servidor API corriendo en: http://localhost:${PORT}`);
});