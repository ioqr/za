import express from 'express'
import morgan from 'morgan'
import fetch from 'node-fetch'

const sendRequest = async (clientResponse, method, url, headers) => {
    const proxyResponse = await fetch(url, { method, headers })
    return {
        headers: proxyResponse.headers,
        status: proxyResponse.status,
        body: await proxyResponse.text()
    }
}

const main = async () => {
    const app = express()
    app.use(morgan('combined'))
    app.post('/za', async (req, res) => {
        let method = req.header('x-za-method')
        method = Buffer.from(method, 'base64').toString('utf8')
        let url = req.header('x-za-url');
        url = Buffer.from(url, 'base64').toString('utf8')
        console.log(url)
        const headerCount = req.header('x-za-headercount')
        const headers = {}
        for (let i = 0; i < headerCount; i++) {
            let key = req.header('x-za-hk-' + i)
            let val = req.header('x-za-hv-' + i)
            key = Buffer.from(key, 'base64').toString('utf8')
            val = Buffer.from(val, 'base64').toString('utf8')
            // console.log(key, val)
            headers[key] = val
        }
        console.log({headers})
        Object.freeze(headers)
        const proxyResponse = await sendRequest(res, method, url, headers)
        for (let header of Object.keys(proxyResponse.headers)) {
            res.set(header, proxyResponse.headers[header])
        }
        res.status(proxyResponse.status)
        res.send(proxyResponse.body)
    })
    app.listen(45678, () => console.log('port 45678'))
}

main()
