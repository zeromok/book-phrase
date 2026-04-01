import client from './client'

export const signup = (data) => client.post('/api/v1/auth/signup', data)
export const login = (data) => client.post('/api/v1/auth/login', data)
